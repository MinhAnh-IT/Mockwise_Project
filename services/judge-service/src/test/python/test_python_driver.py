"""End-to-end smoke tests for UniversalPythonDriver.

Each test injects a minimal `class Solution` into the driver template,
runs the merged source as a Python subprocess (just like Judge0 does),
and asserts on stdout.

Usage:
    cd services/judge-service
    python -m unittest src/test/python/test_python_driver.py -v

Or run the file directly:
    python services/judge-service/src/test/python/test_python_driver.py

Requires Python 3.8+ (matches the Judge0 sandbox runtime).
"""
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path

# Locate the driver template relative to this test file.
HERE = Path(__file__).resolve().parent
DRIVER_PATH = HERE.parent.parent / "main" / "resources" / "drivers" / "UniversalPythonDriver.py"
MARKER = "# === USER_CODE_INJECTED_HERE ==="


def _run(solution_code: str, meta: dict, param_lines):
    """Inject solution_code into the driver template, run via stdin protocol,
    return (returncode, stdout, stderr)."""
    template = DRIVER_PATH.read_text(encoding="utf-8")
    if MARKER not in template:
        raise AssertionError(f"Marker {MARKER!r} missing from driver — test setup bug")
    full_source = template.replace(MARKER, solution_code)

    # Batch protocol: meta line, then T=1, then the single case's param lines.
    stdin_payload = json.dumps(meta) + "\n1\n"
    for line in param_lines:
        stdin_payload += line + "\n"

    proc = subprocess.run(
        [sys.executable, "-c", full_source],
        input=stdin_payload,
        capture_output=True,
        text=True,
        timeout=10,
    )
    # Unwrap the single RS-framed case so existing assertions stay unchanged.
    return proc.returncode, _unwrap(proc.stdout), proc.stderr


def _unwrap(raw: str) -> str:
    """Extract the body of the first (and only) RS-framed case: '\\x1eOK\\n<body>'."""
    chunks = [c for c in raw.split("\x1e") if c != ""]
    if not chunks:
        return raw
    chunk = chunks[0]
    nl = chunk.find("\n")
    return chunk[nl + 1:] if nl >= 0 else ""


def _ok(testcase: unittest.TestCase, code: str, meta: dict, params, expected_stdout: str):
    rc, out, err = _run(code, meta, params)
    testcase.assertEqual(rc, 0, f"non-zero exit\nSTDERR:\n{err}")
    testcase.assertEqual(out, expected_stdout + "\n", f"STDERR:\n{err}")


# ── Primitives ────────────────────────────────────────────────────────────

class PrimitiveTests(unittest.TestCase):

    def test_int_add(self):
        code = "class Solution:\n    def add(self, a, b): return a + b\n"
        meta = {"fn": "add", "return": "int", "inPlace": False,
                "params": [{"name": "a", "type": "int"}, {"name": "b", "type": "int"}]}
        _ok(self, code, meta, ["3", "5"], "8")

    def test_int_negative(self):
        code = "class Solution:\n    def neg(self, n): return -n\n"
        meta = {"fn": "neg", "return": "int", "inPlace": False,
                "params": [{"name": "n", "type": "int"}]}
        _ok(self, code, meta, ["-7"], "7")

    def test_long_boundary(self):
        code = "class Solution:\n    def echo(self, n): return n\n"
        meta = {"fn": "echo", "return": "long", "inPlace": False,
                "params": [{"name": "n", "type": "long"}]}
        _ok(self, code, meta, ["9223372036854775807"], "9223372036854775807")

    def test_double(self):
        code = "class Solution:\n    def echo(self, x): return x\n"
        meta = {"fn": "echo", "return": "double", "inPlace": False,
                "params": [{"name": "x", "type": "double"}]}
        _ok(self, code, meta, ["3.14"], "3.14")

    def test_boolean_true(self):
        code = "class Solution:\n    def neg(self, b): return not b\n"
        meta = {"fn": "neg", "return": "boolean", "inPlace": False,
                "params": [{"name": "b", "type": "boolean"}]}
        _ok(self, code, meta, ["true"], "false")

    def test_boolean_false(self):
        code = "class Solution:\n    def neg(self, b): return not b\n"
        meta = {"fn": "neg", "return": "boolean", "inPlace": False,
                "params": [{"name": "b", "type": "boolean"}]}
        _ok(self, code, meta, ["false"], "true")


# ── String / char ─────────────────────────────────────────────────────────

class StringCharTests(unittest.TestCase):

    def test_string_passthrough(self):
        code = "class Solution:\n    def echo(self, s): return s\n"
        meta = {"fn": "echo", "return": "String", "inPlace": False,
                "params": [{"name": "s", "type": "String"}]}
        _ok(self, code, meta, ["hello world"], "hello world")

    def test_string_empty(self):
        code = "class Solution:\n    def echo(self, s): return s\n"
        meta = {"fn": "echo", "return": "String", "inPlace": False,
                "params": [{"name": "s", "type": "String"}]}
        _ok(self, code, meta, [""], "")

    def test_string_to_boolean(self):
        # isValid-style: parens balance
        code = (
            "class Solution:\n"
            "    def isValid(self, s):\n"
            "        st = []\n"
            "        m = {')':'(', ']':'[', '}':'{'}\n"
            "        for c in s:\n"
            "            if c in m:\n"
            "                if not st or st.pop() != m[c]: return False\n"
            "            else:\n"
            "                st.append(c)\n"
            "        return not st\n"
        )
        meta = {"fn": "isValid", "return": "boolean", "inPlace": False,
                "params": [{"name": "s", "type": "String"}]}
        _ok(self, code, meta, ["()[]{}"], "true")
        _ok(self, code, meta, ["(]"], "false")

    def test_char_input_and_return(self):
        code = "class Solution:\n    def upper(self, c): return c.upper()\n"
        meta = {"fn": "upper", "return": "char", "inPlace": False,
                "params": [{"name": "c", "type": "char"}]}
        _ok(self, code, meta, ["a"], "A")


# ── 1-D arrays ────────────────────────────────────────────────────────────

class ArrayTests(unittest.TestCase):

    def test_int_array(self):
        code = "class Solution:\n    def reverse(self, a): return list(reversed(a))\n"
        meta = {"fn": "reverse", "return": "int[]", "inPlace": False,
                "params": [{"name": "a", "type": "int[]"}]}
        _ok(self, code, meta, ["[1,2,3]"], "[3,2,1]")

    def test_int_array_empty(self):
        code = "class Solution:\n    def reverse(self, a): return list(reversed(a))\n"
        meta = {"fn": "reverse", "return": "int[]", "inPlace": False,
                "params": [{"name": "a", "type": "int[]"}]}
        _ok(self, code, meta, ["[]"], "[]")

    def test_long_array(self):
        code = "class Solution:\n    def echo(self, a): return a\n"
        meta = {"fn": "echo", "return": "long[]", "inPlace": False,
                "params": [{"name": "a", "type": "long[]"}]}
        _ok(self, code, meta, ["[9000000000,1]"], "[9000000000,1]")

    def test_double_array(self):
        code = "class Solution:\n    def echo(self, a): return a\n"
        meta = {"fn": "echo", "return": "double[]", "inPlace": False,
                "params": [{"name": "a", "type": "double[]"}]}
        _ok(self, code, meta, ["[1.5,2.5]"], "[1.5,2.5]")

    def test_string_array(self):
        code = "class Solution:\n    def echo(self, a): return a\n"
        meta = {"fn": "echo", "return": "String[]", "inPlace": False,
                "params": [{"name": "a", "type": "String[]"}]}
        _ok(self, code, meta, ['["a","b","c"]'], '["a","b","c"]')

    def test_two_sum_orderless(self):
        # Drives both int[] input and int[] return
        code = (
            "class Solution:\n"
            "    def twoSum(self, nums, target):\n"
            "        seen = {}\n"
            "        for i, n in enumerate(nums):\n"
            "            d = target - n\n"
            "            if d in seen: return [seen[d], i]\n"
            "            seen[n] = i\n"
            "        return []\n"
        )
        meta = {"fn": "twoSum", "return": "int[]", "inPlace": False,
                "params": [{"name": "nums", "type": "int[]"}, {"name": "target", "type": "int"}]}
        _ok(self, code, meta, ["[2,7,11,15]", "9"], "[0,1]")


# ── 2-D arrays ────────────────────────────────────────────────────────────

class MatrixTests(unittest.TestCase):

    def test_int_matrix_input(self):
        code = (
            "class Solution:\n"
            "    def spiralOrder(self, m):\n"
            "        res = []\n"
            "        while m:\n"
            "            res += m.pop(0)\n"
            "            m = list(zip(*m))[::-1]\n"
            "            m = [list(r) for r in m]\n"
            "        return res\n"
        )
        meta = {"fn": "spiralOrder", "return": "int[]", "inPlace": False,
                "params": [{"name": "m", "type": "int[][]"}]}
        _ok(self, code, meta, ["[[1,2,3],[4,5,6],[7,8,9]]"],
            "[1,2,3,6,9,8,7,4,5]")

    def test_char_matrix_input(self):
        code = (
            "class Solution:\n"
            "    def countX(self, board):\n"
            "        return sum(c == 'X' for row in board for c in row)\n"
        )
        meta = {"fn": "countX", "return": "int", "inPlace": False,
                "params": [{"name": "board", "type": "char[][]"}]}
        _ok(self, code, meta, ['[["X","O"],["X","X"]]'], "3")

    def test_string_matrix_passthrough(self):
        code = "class Solution:\n    def echo(self, m): return m\n"
        meta = {"fn": "echo", "return": "String[][]", "inPlace": False,
                "params": [{"name": "m", "type": "String[][]"}]}
        _ok(self, code, meta, ['[["eat","tea"],["bat"]]'],
            '[["eat","tea"],["bat"]]')

    def test_pep585_generic_annotation_on_3_8(self):
        # User uses Python 3.9+ generic syntax (`list[list[str]]`). The Judge0
        # sandbox runs Python 3.8, which would normally raise TypeError when
        # the class body is evaluated. The driver injects
        # `from __future__ import annotations`, deferring annotation evaluation,
        # so the class definition succeeds.
        code = (
            "class Solution:\n"
            "    def countLand(self, grid: list[list[str]]) -> int:\n"
            "        return sum(c == '1' for row in grid for c in row)\n"
        )
        meta = {"fn": "countLand", "return": "int", "inPlace": False,
                "params": [{"name": "grid", "type": "char[][]"}]}
        _ok(self, code, meta, ['[["1","0","1"],["0","1","1"]]'], "4")


# ── List<...> ─────────────────────────────────────────────────────────────

class ListTypeTests(unittest.TestCase):

    def test_list_integer(self):
        code = "class Solution:\n    def doubleAll(self, xs): return [x*2 for x in xs]\n"
        meta = {"fn": "doubleAll", "return": "List<Integer>", "inPlace": False,
                "params": [{"name": "xs", "type": "List<Integer>"}]}
        _ok(self, code, meta, ["[1,2,3]"], "[2,4,6]")

    def test_list_string(self):
        code = "class Solution:\n    def upper(self, ws): return [w.upper() for w in ws]\n"
        meta = {"fn": "upper", "return": "List<String>", "inPlace": False,
                "params": [{"name": "ws", "type": "List<String>"}]}
        _ok(self, code, meta, ['["a","bc"]'], '["A","BC"]')

    def test_list_list_integer(self):
        code = "class Solution:\n    def echo(self, x): return x\n"
        meta = {"fn": "echo", "return": "List<List<Integer>>", "inPlace": False,
                "params": [{"name": "x", "type": "List<List<Integer>>"}]}
        _ok(self, code, meta, ["[[1,2],[3,4]]"], "[[1,2],[3,4]]")

    def test_list_list_string_group_anagrams(self):
        code = (
            "class Solution:\n"
            "    def groupAnagrams(self, strs):\n"
            "        from collections import defaultdict\n"
            "        d = defaultdict(list)\n"
            "        for s in strs:\n"
            "            d[''.join(sorted(s))].append(s)\n"
            "        return [sorted(v) for v in sorted(d.values(), key=lambda v: sorted(v))]\n"
        )
        meta = {"fn": "groupAnagrams", "return": "List<List<String>>", "inPlace": False,
                "params": [{"name": "strs", "type": "String[]"}]}
        _ok(self, code, meta, ['["eat","tea","tan","ate","nat","bat"]'],
            '[["ate","eat","tea"],["bat"],["nat","tan"]]')


# ── TreeNode ──────────────────────────────────────────────────────────────

class TreeNodeTests(unittest.TestCase):

    def test_max_depth(self):
        code = (
            "class Solution:\n"
            "    def maxDepth(self, root):\n"
            "        if root is None: return 0\n"
            "        return 1 + max(self.maxDepth(root.left), self.maxDepth(root.right))\n"
        )
        meta = {"fn": "maxDepth", "return": "int", "inPlace": False,
                "params": [{"name": "root", "type": "TreeNode"}]}
        _ok(self, code, meta, ["[3,9,20,null,null,15,7]"], "3")
        _ok(self, code, meta, ["[]"], "0")
        _ok(self, code, meta, ["[1,null,2]"], "2")

    def test_invert_tree_returns_treenode_levelorder(self):
        code = (
            "class Solution:\n"
            "    def invertTree(self, root):\n"
            "        if root is None: return None\n"
            "        root.left, root.right = self.invertTree(root.right), self.invertTree(root.left)\n"
            "        return root\n"
        )
        meta = {"fn": "invertTree", "return": "TreeNode", "inPlace": False,
                "params": [{"name": "root", "type": "TreeNode"}]}
        _ok(self, code, meta, ["[4,2,7,1,3,6,9]"], "[4,7,2,9,6,3,1]")

    def test_treenode_null_return(self):
        code = "class Solution:\n    def f(self, root): return None\n"
        meta = {"fn": "f", "return": "TreeNode", "inPlace": False,
                "params": [{"name": "root", "type": "TreeNode"}]}
        _ok(self, code, meta, ["[1]"], "[]")


# ── ListNode ──────────────────────────────────────────────────────────────

class ListNodeTests(unittest.TestCase):

    def test_reverse_list(self):
        code = (
            "class Solution:\n"
            "    def reverseList(self, head):\n"
            "        prev = None\n"
            "        while head:\n"
            "            nxt = head.next\n"
            "            head.next = prev\n"
            "            prev = head\n"
            "            head = nxt\n"
            "        return prev\n"
        )
        meta = {"fn": "reverseList", "return": "ListNode", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}]}
        _ok(self, code, meta, ["[1,2,3,4,5]"], "[5,4,3,2,1]")

    def test_reverse_list_empty(self):
        code = (
            "class Solution:\n"
            "    def reverseList(self, head):\n"
            "        prev = None\n"
            "        while head:\n"
            "            nxt = head.next\n"
            "            head.next = prev\n"
            "            prev = head\n"
            "            head = nxt\n"
            "        return prev\n"
        )
        meta = {"fn": "reverseList", "return": "ListNode", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}]}
        _ok(self, code, meta, ["[]"], "[]")

    def test_listnode_input_int_return(self):
        code = (
            "class Solution:\n"
            "    def length(self, head):\n"
            "        n = 0\n"
            "        while head:\n"
            "            n += 1\n"
            "            head = head.next\n"
            "        return n\n"
        )
        meta = {"fn": "length", "return": "int", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}]}
        _ok(self, code, meta, ["[10,20,30]"], "3")
        _ok(self, code, meta, ["[]"], "0")

    def test_construct_new_listnode_using_driver_class(self):
        # Solution constructs its own ListNode using the driver-provided class.
        code = (
            "class Solution:\n"
            "    def doubleVals(self, head):\n"
            "        dummy = ListNode(0)\n"
            "        cur = dummy\n"
            "        while head:\n"
            "            cur.next = ListNode(head.val * 2)\n"
            "            cur = cur.next\n"
            "            head = head.next\n"
            "        return dummy.next\n"
        )
        meta = {"fn": "doubleVals", "return": "ListNode", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}]}
        _ok(self, code, meta, ["[1,2,3]"], "[2,4,6]")

    def test_listnode_returns_null(self):
        code = "class Solution:\n    def empty(self, head): return None\n"
        meta = {"fn": "empty", "return": "ListNode", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}]}
        _ok(self, code, meta, ["[1,2,3]"], "[]")

    def test_reverse_empty_with_user_redefined_listnode(self):
        # Regression: user code redefines `class ListNode`, shadowing the driver's.
        # Empty input → reverseList returns None → driver must still print "[]".
        code = (
            "class ListNode:\n"
            "    def __init__(self, val=0, next=None):\n"
            "        self.val = val\n"
            "        self.next = next\n"
            "\n"
            "class Solution:\n"
            "    def reverseList(self, head):\n"
            "        prev = None\n"
            "        curr = head\n"
            "        while curr:\n"
            "            nxt = curr.next\n"
            "            curr.next = prev\n"
            "            prev = curr\n"
            "            curr = nxt\n"
            "        return prev\n"
        )
        meta = {"fn": "reverseList", "return": "ListNode", "inPlace": False,
                "params": [{"name": "head", "type": "ListNode"}],
                "orderMatters": True}
        _ok(self, code, meta, ["[]"], "[]")
        # And the non-empty case still works for the same code.
        _ok(self, code, meta, ["[1,2,3,4,5]"], "[5,4,3,2,1]")


# ── inPlace ───────────────────────────────────────────────────────────────

class InPlaceTests(unittest.TestCase):

    def test_sort_colors_inplace(self):
        code = (
            "class Solution:\n"
            "    def sortColors(self, nums):\n"
            "        nums.sort()\n"
        )
        meta = {"fn": "sortColors", "return": "void", "inPlace": True,
                "params": [{"name": "nums", "type": "int[]"}]}
        _ok(self, code, meta, ["[2,0,2,1,1,0]"], "[0,0,1,1,2,2]")


# ── Edge cases & failure modes ────────────────────────────────────────────

class EdgeCaseTests(unittest.TestCase):

    def test_missing_solution_class_gives_clear_runtime_error(self):
        # User wrote a top-level def — driver enforces class Solution.
        code = "def twoSum(nums, target): return []\n"
        meta = {"fn": "twoSum", "return": "int[]", "inPlace": False,
                "params": [{"name": "nums", "type": "int[]"}, {"name": "target", "type": "int"}]}
        # Batch isolation: the per-case error is reported in the ERR frame body
        # (stdout) and the process still exits 0.
        rc, out, err = _run(code, meta, ["[1,2]", "3"])
        self.assertIn("Solution", out)

    def test_method_not_on_solution(self):
        code = "class Solution:\n    def other(self): pass\n"
        meta = {"fn": "twoSum", "return": "int[]", "inPlace": False,
                "params": [{"name": "nums", "type": "int[]"}]}
        rc, out, err = _run(code, meta, ["[1,2]"])
        self.assertIn("twoSum", out)


if __name__ == "__main__":
    if not DRIVER_PATH.exists():
        print(f"Driver not found at {DRIVER_PATH}", file=sys.stderr)
        sys.exit(2)
    unittest.main(verbosity=2)
