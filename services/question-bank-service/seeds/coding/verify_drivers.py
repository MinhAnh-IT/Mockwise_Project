#!/usr/bin/env python3
"""Cross-language driver verification.

Replicates judge-service `CodeBuilder` (Java/JS/C++ assembly + C++ dispatch
codegen) and runs hand-written reference solutions in all three remaining
languages through the *real* Universal*Driver files, comparing each output to
the already-verified expected output in coding_questions.json via the same
OutputComparator logic the judge uses.

Goal: catch judge-service driver bugs (esp. the C++ baked-in dispatch and the
language serializers) across every supported param/return type — locally, with
no impact on prod. Any mismatch that is the driver's fault (not the solution's)
is a judge bug to fix.

  python3 verify_drivers.py            # all languages
  python3 verify_drivers.py cpp        # one language
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from engine import build_stdin, comparator_accepts

REPO = Path(__file__).resolve().parents[4]
DRV = REPO / "services/judge-service/src/main/resources/drivers"
JAVA_DRV = (DRV / "UniversalJavaDriver.java").read_text()
JS_DRV = (DRV / "UniversalJsDriver.js").read_text()
CPP_DRV = (DRV / "UniversalCppDriver.cpp").read_text()

CASES_PER_PROBLEM = 10000  # all cases — full 4-language parity check


# ── CodeBuilder replication ────────────────────────────────────────────────
def build_java(user_code):
    import re
    user = re.sub(r"(?m)^(\s*)public\s+(class\s+Solution\b)", r"\1\2", user_code)
    imports, body, past = [], [], False
    for line in JAVA_DRV.split("\n"):
        t = line.strip()
        if not past and (t.startswith("import ") or t == ""):
            imports.append(line)
        else:
            past = True
            body.append(line)
    return "\n".join(imports) + "\n" + user + "\n\n" + "\n".join(body)


def build_js(user_code):
    return JS_DRV.replace("// === USER_CODE_INJECTED_HERE ===", user_code)


_CPP_TYPE = {
    "int": "int", "long": "long long", "double": "double", "boolean": "bool",
    "string": "std::string", "char": "char", "int[]": "std::vector<int>",
    "long[]": "std::vector<long long>", "double[]": "std::vector<double>",
    "string[]": "std::vector<std::string>", "int[][]": "std::vector<std::vector<int>>",
    "char[][]": "std::vector<std::vector<char>>",
    "string[][]": "std::vector<std::vector<std::string>>",
    "List<Integer>": "std::vector<int>", "List<String>": "std::vector<std::string>",
    "List<List<Integer>>": "std::vector<std::vector<int>>",
    "List<List<String>>": "std::vector<std::vector<std::string>>",
    "TreeNode": "TreeNode*", "ListNode": "ListNode*",
}
_CPP_PARSE = {
    "int": "std::stoi({v})", "long": "std::stoll({v})", "double": "std::stod({v})",
    "boolean": "(_judge::trim({v}) == \"true\")", "string": "{v}",
    "char": "({v}.empty() ? '\\0' : {v}[0])",
    "int[]": "_judge::parseIntArray({v})", "List<Integer>": "_judge::parseIntArray({v})",
    "long[]": "_judge::parseLongArray({v})", "double[]": "_judge::parseDoubleArray({v})",
    "string[]": "_judge::parseStringArray({v})", "List<String>": "_judge::parseStringArray({v})",
    "int[][]": "_judge::parseIntMatrix({v})",
    "List<List<Integer>>": "_judge::parseIntMatrix({v})",
    "char[][]": "_judge::parseCharMatrix({v})",
    "string[][]": "_judge::parseStringMatrix({v})",
    "List<List<String>>": "_judge::parseStringMatrix({v})",
    "TreeNode": "_judge::buildTree({v})", "ListNode": "_judge::buildList({v})",
}
_CPP_NORM = {"String": "string", "String[]": "string[]", "String[][]": "string[][]",
             "Integer": "int", "Long": "long", "Double": "double", "Boolean": "boolean",
             "Character": "char"}


def _cpp_norm(t):
    return _CPP_NORM.get(t, t) if t else "void"


def gen_cpp_dispatch(meta):
    params = meta["params"]  # list of (name, type)
    out = []
    for i, (name, ptype) in enumerate(params):
        t = _cpp_norm(ptype)
        out.append(f'    std::string _line{i}; std::getline(std::cin, _line{i});')
        out.append(f'    {_CPP_TYPE[t]} {name} = {_CPP_PARSE[t].format(v="_line"+str(i))};')
    args = ", ".join(name for name, _ in params)
    out.append("    Solution sol;")
    rt = _cpp_norm(meta["return"])
    if meta.get("inPlace"):
        out.append(f"    sol.{meta['fn']}({args});")
        out.append(f'    std::cout << _judge::toJson({params[0][0]}) << "\\n";')
    elif rt == "void":
        out.append(f"    sol.{meta['fn']}({args});")
        out.append('    std::cout << "null" << "\\n";')
    else:
        out.append(f"    auto _result = sol.{meta['fn']}({args});")
        out.append('    std::cout << _judge::toJson(_result) << "\\n";')
    return "\n".join(out) + "\n"


def build_cpp(user_code, meta):
    return (CPP_DRV
            .replace("// === USER_CODE_INJECTED_HERE ===", user_code)
            .replace("    // === DISPATCH_INJECTED_HERE ===", gen_cpp_dispatch(meta)))


# ── compile + run ──────────────────────────────────────────────────────────
class Lang:
    def __init__(self, tmp):
        self.tmp = Path(tmp)

    def prep(self, title, meta, src_user):
        raise NotImplementedError

    def run(self, stdin):
        raise NotImplementedError


class JavaLang(Lang):
    def prep(self, title, meta, user):
        f = self.tmp / "Main.java"
        f.write_text(build_java(user))
        r = subprocess.run(["javac", str(f)], cwd=self.tmp, capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("javac:\n" + r.stderr)

    def run(self, stdin):
        return subprocess.run(["java", "-cp", str(self.tmp), "Main"], input=stdin,
                              capture_output=True, text=True, timeout=20).stdout


class JsLang(Lang):
    def prep(self, title, meta, user):
        self.f = self.tmp / "sol.js"
        self.f.write_text(build_js(user))

    def run(self, stdin):
        return subprocess.run(["node", str(self.f)], input=stdin,
                              capture_output=True, text=True, timeout=20).stdout


class CppLang(Lang):
    def prep(self, title, meta, user):
        f = self.tmp / "sol.cpp"
        f.write_text(build_cpp(user, meta))
        self.bin = self.tmp / "sol"
        r = subprocess.run(["g++", "-std=c++17", "-O2", str(f), "-o", str(self.bin)],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("g++:\n" + r.stderr)

    def run(self, stdin):
        return subprocess.run([str(self.bin)], input=stdin,
                              capture_output=True, text=True, timeout=20).stdout


LANGS = {"java": JavaLang, "js": JsLang, "cpp": CppLang}


# ── main ───────────────────────────────────────────────────────────────────
def meta_tuple(fm):
    return {
        "fn": fm["fn"],
        "params": [(p["name"], p["type"]) for p in fm["params"]],
        "return": fm["return"],
        "orderMatters": fm.get("orderMatters", False),
        "inPlace": fm.get("inPlace", False),
    }


def main():
    from solutions_all import SOLUTIONS
    langs = sys.argv[1:] or ["java", "js", "cpp"]
    data = {d["title"]: d for d in json.load(open(Path(__file__).parent / "coding_questions.json"))}

    total_fail = 0
    for title, sols in SOLUTIONS.items():
        if title not in data:
            print(f"  ?? {title}: not in coding_questions.json — skipped")
            continue
        q = data[title]
        meta = meta_tuple(q["functionMeta"])
        cases = q["testCases"][:CASES_PER_PROBLEM]
        line = f"{title[:42]:<42}"
        cells = []
        for lang in langs:
            if lang not in sols:
                cells.append(f"{lang}:--")
                continue
            with tempfile.TemporaryDirectory() as tmp:
                runner = LANGS[lang](tmp)
                try:
                    runner.prep(title, meta, sols[lang])
                except RuntimeError as e:
                    cells.append(f"{lang}:COMPILE-ERR")
                    total_fail += 1
                    print(f"  XX {line} {lang} COMPILE\n{str(e)[:400]}")
                    continue
                ok = 0
                bad = None
                for tc in cases:
                    stdin = build_stdin(meta, tc["inputData"])
                    out = runner.run(stdin)
                    exp = tc["expectedOutput"]["result"]
                    if comparator_accepts(out, exp, meta["orderMatters"]):
                        ok += 1
                    else:
                        bad = (tc["inputData"], exp, out)
                        break
                if bad is None:
                    cells.append(f"{lang}:{ok}/{len(cases)}")
                else:
                    cells.append(f"{lang}:FAIL")
                    total_fail += 1
                    print(f"  XX {line} {lang} WA input={json.dumps(bad[0])[:80]} "
                          f"expected={json.dumps(bad[1])[:60]} got={bad[2].strip()[:60]!r}")
        status = "OK " if "FAIL" not in " ".join(cells) and "ERR" not in " ".join(cells) else "XX "
        print(f"  {status}{line} " + "  ".join(cells))

    print(f"\n{'ALL DRIVERS PASS' if total_fail == 0 else f'{total_fail} FAILURES'}")
    sys.exit(1 if total_fail else 0)


if __name__ == "__main__":
    main()
