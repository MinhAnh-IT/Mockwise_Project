"use strict";

// ── Struct definitions (available to user code) ───────────────────────────

class TreeNode {
    constructor(val, left, right) {
        this.val = (val === undefined ? 0 : val);
        this.left = (left === undefined ? null : left);
        this.right = (right === undefined ? null : right);
    }
}

class ListNode {
    constructor(val, next) {
        this.val = (val === undefined ? 0 : val);
        this.next = (next === undefined ? null : next);
    }
}

// === USER_CODE_INJECTED_HERE ===


// ── Driver internals ──────────────────────────────────────────────────────

function _normalizeType(t) {
    var map = {
        "String": "string",
        "String[]": "string[]",
        "String[][]": "string[][]",
        "Boolean": "boolean",
        "Integer": "int",
        "Long": "long",
        "Double": "double",
        "Character": "char"
    };
    return Object.prototype.hasOwnProperty.call(map, t) ? map[t] : t;
}

function _parseValue(raw, type) {
    var t = _normalizeType(type);
    if (t === "int" || t === "long" || t === "double") return Number(raw);
    if (t === "boolean") return String(raw).trim().toLowerCase() === "true";
    if (t === "string") return raw;
    if (t === "char") return raw && raw.length > 0 ? raw[0] : "";
    if (
        t === "int[]" || t === "long[]" || t === "double[]" ||
        t === "string[]" ||
        t === "int[][]" || t === "char[][]" || t === "string[][]" ||
        t === "List<Integer>" || t === "List<String>" ||
        t === "List<List<Integer>>" || t === "List<List<String>>"
    ) {
        return JSON.parse(raw);
    }
    if (t === "TreeNode") return _buildTree(JSON.parse(raw));
    if (t === "ListNode") return _buildList(JSON.parse(raw));
    return raw;
}

function _buildTree(vals) {
    if (!vals || vals.length === 0 || vals[0] === null) return null;
    var root = new TreeNode(vals[0]);
    var q = [root];
    var head = 0;
    var i = 1;
    while (head < q.length && i < vals.length) {
        var node = q[head++];
        if (i < vals.length && vals[i] !== null) {
            node.left = new TreeNode(vals[i]);
            q.push(node.left);
        }
        i++;
        if (i < vals.length && vals[i] !== null) {
            node.right = new TreeNode(vals[i]);
            q.push(node.right);
        }
        i++;
    }
    return root;
}

function _serializeTree(root) {
    if (root === null || root === undefined) return "[]";
    var res = [];
    var q = [root];
    var head = 0;
    while (head < q.length) {
        var node = q[head++];
        if (node === null || node === undefined) {
            res.push(null);
            continue;
        }
        res.push(node.val);
        q.push(node.left);
        q.push(node.right);
    }
    while (res.length > 0 && res[res.length - 1] === null) res.pop();
    return JSON.stringify(res);
}

function _buildList(vals) {
    if (!vals || vals.length === 0) return null;
    var dummy = new ListNode(0);
    var cur = dummy;
    for (var i = 0; i < vals.length; i++) {
        cur.next = new ListNode(vals[i]);
        cur = cur.next;
    }
    return dummy.next;
}

function _serializeList(head) {
    var res = [];
    while (head !== null && head !== undefined) {
        res.push(head.val);
        head = head.next;
    }
    return JSON.stringify(res);
}

// Match UniversalJavaDriver.toJson:
//   - top-level string/char: raw, no surrounding quotes
//   - numbers/booleans/arrays: standard JSON, compact (no spaces)
//   - TreeNode/ListNode: custom serializers
function _toJsonTopLevel(val) {
    if (val === null || val === undefined) return "null";
    if (typeof val === "string") return val;
    if (val instanceof TreeNode) return _serializeTree(val);
    if (val instanceof ListNode) return _serializeList(val);
    return JSON.stringify(val);
}

// Returns true when `t` denotes a structural type whose null serializes as "[]".
// Accepts canonical names plus a few common variants users tend to write.
function _isNodeType(t) {
    if (!t) return false;
    var norm = String(t).trim().toLowerCase().replace(/_/g, "").replace(/-/g, "");
    return norm === "listnode" || norm === "treenode" ||
        norm === "linkedlist" || norm === "binarytree";
}

function _main() {
    var fs = require("fs");
    // Read entire stdin synchronously — Node.js 12.14 (Judge0 sandbox) supports fd 0.
    var data = fs.readFileSync(0, "utf8");
    var lines = data.split("\n");

    var meta = JSON.parse(lines[0]);
    var fnName = meta.fn;
    var returnType = meta["return"] || "";
    var inPlace = !!meta.inPlace;
    var paramsMeta = meta.params || [];

    var args = [];
    for (var i = 0; i < paramsMeta.length; i++) {
        var raw = (1 + i) < lines.length ? lines[1 + i] : "";
        if (raw.length > 0 && raw.charAt(raw.length - 1) === "\r") {
            raw = raw.substring(0, raw.length - 1);
        }
        // Mirror Java/Python drivers: trim each line, except for raw string params
        // where leading/trailing spaces are theoretically meaningful.
        if (_normalizeType(paramsMeta[i].type) !== "string") {
            raw = raw.trim();
        }
        args.push(_parseValue(raw, paramsMeta[i].type));
    }

    var sol = new Solution();
    var method = sol[fnName];
    if (typeof method !== "function") {
        throw new Error("Method '" + fnName + "' not found on Solution");
    }
    var result = method.apply(sol, args);

    var out;
    if (inPlace) {
        out = _toJsonTopLevel(args[0]);
    } else if ((result === null || result === undefined) && _isNodeType(returnType)) {
        // Empty linked list / empty tree → "[]" (matches Java/Python driver convention).
        out = "[]";
    } else {
        out = _toJsonTopLevel(result);
    }

    process.stdout.write(out + "\n");
}

_main();
