# PEP 563: defer evaluation of annotations to strings. Lets user solutions use
# Python 3.9+ generic syntax (list[int], dict[str, int], etc.) on the Judge0
# Python 3.8 sandbox without raising TypeError at class-definition time.
from __future__ import annotations

import sys
import json
from collections import deque


# ── Struct definitions (available to user code) ───────────────────────────

class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val = val
        self.left = left
        self.right = right


class ListNode:
    def __init__(self, val=0, next=None):
        self.val = val
        self.next = next


# === USER_CODE_INJECTED_HERE ===


# ── Driver internals ──────────────────────────────────────────────────────

def _normalize_type(t):
    return {
        "String": "string",
        "String[]": "string[]",
        "String[][]": "string[][]",
        "Boolean": "boolean",
        "Integer": "int",
        "Long": "long",
        "Double": "double",
        "Character": "char",
    }.get(t, t)


def _parse_value(raw, t):
    t = _normalize_type(t)
    if t in ("int", "long"):
        return int(raw)
    if t == "double":
        return float(raw)
    if t == "boolean":
        return raw.strip().lower() == "true"
    if t == "string":
        return raw
    if t == "char":
        return raw[0] if raw else ""
    if t in (
        "int[]", "long[]", "double[]",
        "string[]", "int[][]", "char[][]", "string[][]",
        "List<Integer>", "List<String>",
        "List<List<Integer>>", "List<List<String>>",
    ):
        return json.loads(raw)
    if t == "TreeNode":
        return _build_tree(json.loads(raw))
    if t == "ListNode":
        return _build_list(json.loads(raw))
    return raw


def _build_tree(vals):
    if not vals or vals[0] is None:
        return None
    root = TreeNode(vals[0])
    q = deque([root])
    i = 1
    while q and i < len(vals):
        node = q.popleft()
        if i < len(vals) and vals[i] is not None:
            node.left = TreeNode(vals[i])
            q.append(node.left)
        i += 1
        if i < len(vals) and vals[i] is not None:
            node.right = TreeNode(vals[i])
            q.append(node.right)
        i += 1
    return root


def _serialize_tree(root):
    if root is None:
        return "[]"
    res = []
    q = deque([root])
    while q:
        node = q.popleft()
        if node is None:
            res.append(None)
            continue
        res.append(node.val)
        q.append(node.left)
        q.append(node.right)
    while res and res[-1] is None:
        res.pop()
    return json.dumps(res, separators=(",", ":"))


def _build_list(vals):
    if not vals:
        return None
    dummy = ListNode(0)
    cur = dummy
    for v in vals:
        cur.next = ListNode(v)
        cur = cur.next
    return dummy.next


def _serialize_list(head):
    res = []
    while head is not None:
        res.append(head.val)
        head = head.next
    return json.dumps(res, separators=(",", ":"))


def _to_json_top_level(val):
    """Match UniversalJavaDriver.toJson:
       - top-level string/char: raw, no surrounding quotes
       - numbers/booleans/lists: standard JSON, compact (no spaces)
       - TreeNode/ListNode: custom serializers
    """
    if val is None:
        return "null"
    if isinstance(val, str):
        return val
    if isinstance(val, TreeNode):
        return _serialize_tree(val)
    if isinstance(val, ListNode):
        return _serialize_list(val)
    return json.dumps(val, separators=(",", ":"))


# Record-separator framing for the batch protocol. One Judge0 submission now
# runs ALL test cases of a job in a single process (compile once), so the driver
# must delimit each case's output. Each case emits:
#     \x1e OK \n <serialized output>\n     — success
#     \x1e ERR \n <error message>\n        — the Solution call raised
# 0x1E (RS) never appears in our JSON/answer space, so judge-service splits the
# stdout on it. Per-case stdout is flushed so already-finished cases survive a
# later hard crash (segfault/OOM) — the unflushed remainder is then marked RE.
_RS = "\x1e"


def _run_case(fn_name, return_type, in_place, params_meta, lines, idx):
    args = []
    for i, p in enumerate(params_meta):
        raw = lines[idx + i] if idx + i < len(lines) else ""
        raw = raw.rstrip("\r")
        # Mirror Java driver: trim() per line, except for raw string params
        # where leading/trailing spaces are theoretically meaningful.
        if _normalize_type(p["type"]) != "string":
            raw = raw.strip()
        args.append(_parse_value(raw, p["type"]))

    sol = Solution()
    method = getattr(sol, fn_name)
    result = method(*args)

    if in_place:
        return _to_json_top_level(args[0])
    if result is None and _is_node_type(return_type):
        # Empty linked list / empty tree → "[]" (matches Java driver convention).
        # Covers both the deserialized-empty-input case and explicit `return None`.
        return "[]"
    return _to_json_top_level(result)


def _main():
    data = sys.stdin.read()
    lines = data.split("\n")

    meta = json.loads(lines[0])
    fn_name = meta["fn"]
    return_type = meta.get("return", "")
    in_place = bool(meta.get("inPlace", False))
    params_meta = meta.get("params", [])
    nparams = len(params_meta)

    # Line 2 = number of test cases; then `nparams` lines per case.
    t = int(lines[1].strip())
    idx = 2
    for _ in range(t):
        try:
            body = _run_case(fn_name, return_type, in_place, params_meta, lines, idx)
            sys.stdout.write(_RS + "OK\n" + body + "\n")
        except Exception as e:  # noqa: BLE001 — per-case isolation, one bad case ≠ whole batch
            sys.stdout.write(_RS + "ERR\n" + str(e) + "\n")
        sys.stdout.flush()
        idx += nparams


def _is_node_type(t):
    """Return True when ``t`` denotes a structural type whose null serializes as ``[]``.
    Accepts the canonical names plus a few common variants users tend to write.
    """
    if not t:
        return False
    norm = t.strip().lower().replace("_", "").replace("-", "")
    return norm in ("listnode", "treenode", "linkedlist", "binarytree")


if __name__ == "__main__":
    _main()
