/**
 * End-to-end smoke tests for UniversalJsDriver.
 *
 * Each test injects a minimal `class Solution` into the driver template,
 * runs the merged source as a Node.js subprocess (just like Judge0 does),
 * and asserts on stdout.
 *
 * Usage:
 *     cd services/judge-service
 *     node --test src/test/js/test_js_driver.js
 *
 * Requires Node.js 18+ for the built-in `node:test` runner. The driver
 * itself targets Node.js 12.14 (Judge0 sandbox), so it must remain
 * compatible with that runtime — only the test harness uses newer Node.
 */
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const HERE = __dirname;
const DRIVER_PATH = path.resolve(
    HERE, "..", "..", "main", "resources", "drivers", "UniversalJsDriver.js"
);
const MARKER = "// === USER_CODE_INJECTED_HERE ===";

if (!fs.existsSync(DRIVER_PATH)) {
    console.error(`Driver not found at ${DRIVER_PATH}`);
    process.exit(2);
}

/**
 * Inject solution_code into the driver template, run via stdin protocol,
 * return { code, stdout, stderr }.
 */
function _run(solutionCode, meta, paramLines) {
    const template = fs.readFileSync(DRIVER_PATH, "utf-8");
    if (!template.includes(MARKER)) {
        throw new Error(`Marker ${MARKER} missing from driver — test setup bug`);
    }
    const fullSource = template.replace(MARKER, solutionCode);

    let stdinPayload = JSON.stringify(meta) + "\n";
    for (const line of paramLines) stdinPayload += line + "\n";

    const proc = spawnSync(process.execPath, ["-e", fullSource], {
        input: stdinPayload,
        encoding: "utf-8",
        timeout: 10_000,
    });
    return { code: proc.status, stdout: proc.stdout, stderr: proc.stderr };
}

function _ok(t, code, meta, params, expectedStdout) {
    const { code: rc, stdout, stderr } = _run(code, meta, params);
    assert.equal(rc, 0, `non-zero exit\nSTDERR:\n${stderr}`);
    assert.equal(stdout, expectedStdout + "\n", `STDERR:\n${stderr}`);
}

// ── Primitives ────────────────────────────────────────────────────────────

test("primitives: int add", (t) => {
    const code = "class Solution { add(a, b) { return a + b; } }";
    const meta = {
        fn: "add", return: "int", inPlace: false,
        params: [{ name: "a", type: "int" }, { name: "b", type: "int" }],
    };
    _ok(t, code, meta, ["3", "5"], "8");
});

test("primitives: int negative", (t) => {
    const code = "class Solution { neg(n) { return -n; } }";
    const meta = {
        fn: "neg", return: "int", inPlace: false,
        params: [{ name: "n", type: "int" }],
    };
    _ok(t, code, meta, ["-7"], "7");
});

test("primitives: int zero", (t) => {
    const code = "class Solution { echo(n) { return n; } }";
    const meta = {
        fn: "echo", return: "int", inPlace: false,
        params: [{ name: "n", type: "int" }],
    };
    _ok(t, code, meta, ["0"], "0");
});

test("primitives: int MAX_INT boundary", (t) => {
    const code = "class Solution { echo(n) { return n; } }";
    const meta = {
        fn: "echo", return: "int", inPlace: false,
        params: [{ name: "n", type: "int" }],
    };
    _ok(t, code, meta, ["2147483647"], "2147483647");
});

test("primitives: int MIN_INT boundary", (t) => {
    const code = "class Solution { echo(n) { return n; } }";
    const meta = {
        fn: "echo", return: "int", inPlace: false,
        params: [{ name: "n", type: "int" }],
    };
    _ok(t, code, meta, ["-2147483648"], "-2147483648");
});

test("primitives: long within safe-integer range", (t) => {
    // JS Number.MAX_SAFE_INTEGER = 9007199254740991 (2^53 - 1)
    const code = "class Solution { echo(n) { return n; } }";
    const meta = {
        fn: "echo", return: "long", inPlace: false,
        params: [{ name: "n", type: "long" }],
    };
    _ok(t, code, meta, ["9007199254740991"], "9007199254740991");
});

test("primitives: double", (t) => {
    const code = "class Solution { echo(x) { return x; } }";
    const meta = {
        fn: "echo", return: "double", inPlace: false,
        params: [{ name: "x", type: "double" }],
    };
    _ok(t, code, meta, ["3.14"], "3.14");
});

test("primitives: boolean true → false", (t) => {
    const code = "class Solution { neg(b) { return !b; } }";
    const meta = {
        fn: "neg", return: "boolean", inPlace: false,
        params: [{ name: "b", type: "boolean" }],
    };
    _ok(t, code, meta, ["true"], "false");
});

test("primitives: boolean false → true", (t) => {
    const code = "class Solution { neg(b) { return !b; } }";
    const meta = {
        fn: "neg", return: "boolean", inPlace: false,
        params: [{ name: "b", type: "boolean" }],
    };
    _ok(t, code, meta, ["false"], "true");
});

test("primitives: boxed type aliases (Integer/Boolean) normalize", (t) => {
    const code = "class Solution { mul(a, b) { return a * b; } }";
    const meta = {
        fn: "mul", return: "Integer", inPlace: false,
        params: [{ name: "a", type: "Integer" }, { name: "b", type: "Integer" }],
    };
    _ok(t, code, meta, ["6", "7"], "42");
});

// ── String / char ─────────────────────────────────────────────────────────

test("string: passthrough with spaces", (t) => {
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "String", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, ["hello world"], "hello world");
});

test("string: empty", (t) => {
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "String", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, [""], "");
});

test("string: single character", (t) => {
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "String", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, ["a"], "a");
});

test("string: special characters preserved", (t) => {
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "String", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, ["a+b=c"], "a+b=c");
});

test("string: lowercase alias 'string' is normalized", (t) => {
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "string", inPlace: false,
        params: [{ name: "s", type: "string" }],
    };
    _ok(t, code, meta, ["ok"], "ok");
});

test("string→boolean: parens balance (isValid)", (t) => {
    const code = `
class Solution {
    isValid(s) {
        const st = [];
        const m = { ")": "(", "]": "[", "}": "{" };
        for (const c of s) {
            if (c in m) {
                if (st.pop() !== m[c]) return false;
            } else {
                st.push(c);
            }
        }
        return st.length === 0;
    }
}`;
    const meta = {
        fn: "isValid", return: "boolean", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, ["()[]{}"], "true");
    _ok(t, code, meta, ["(]"], "false");
    _ok(t, code, meta, [""], "true");
});

test("char: input and return (uppercase)", (t) => {
    const code = "class Solution { upper(c) { return c.toUpperCase(); } }";
    const meta = {
        fn: "upper", return: "char", inPlace: false,
        params: [{ name: "c", type: "char" }],
    };
    _ok(t, code, meta, ["a"], "A");
});

test("char: digit char passes through", (t) => {
    const code = "class Solution { echo(c) { return c; } }";
    const meta = {
        fn: "echo", return: "char", inPlace: false,
        params: [{ name: "c", type: "char" }],
    };
    _ok(t, code, meta, ["3"], "3");
});

test("char: 'Character' alias normalizes", (t) => {
    const code = "class Solution { echo(c) { return c; } }";
    const meta = {
        fn: "echo", return: "Character", inPlace: false,
        params: [{ name: "c", type: "Character" }],
    };
    _ok(t, code, meta, ["X"], "X");
});

// ── 1-D arrays ────────────────────────────────────────────────────────────

test("array int[]: reverse", (t) => {
    const code = "class Solution { reverse(a) { return a.slice().reverse(); } }";
    const meta = {
        fn: "reverse", return: "int[]", inPlace: false,
        params: [{ name: "a", type: "int[]" }],
    };
    _ok(t, code, meta, ["[1,2,3]"], "[3,2,1]");
});

test("array int[]: empty", (t) => {
    const code = "class Solution { reverse(a) { return a.slice().reverse(); } }";
    const meta = {
        fn: "reverse", return: "int[]", inPlace: false,
        params: [{ name: "a", type: "int[]" }],
    };
    _ok(t, code, meta, ["[]"], "[]");
});

test("array int[]: single element", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "int[]", inPlace: false,
        params: [{ name: "a", type: "int[]" }],
    };
    _ok(t, code, meta, ["[42]"], "[42]");
});

test("array int[]: with negatives and duplicates", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "int[]", inPlace: false,
        params: [{ name: "a", type: "int[]" }],
    };
    _ok(t, code, meta, ["[-5,-3,1,1,2,2]"], "[-5,-3,1,1,2,2]");
});

test("array long[]: above 32-bit", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "long[]", inPlace: false,
        params: [{ name: "a", type: "long[]" }],
    };
    _ok(t, code, meta, ["[9000000000,1]"], "[9000000000,1]");
});

test("array double[]: passthrough", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "double[]", inPlace: false,
        params: [{ name: "a", type: "double[]" }],
    };
    _ok(t, code, meta, ["[1.5,2.5]"], "[1.5,2.5]");
});

test("array String[]: passthrough", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "String[]", inPlace: false,
        params: [{ name: "a", type: "String[]" }],
    };
    _ok(t, code, meta, [`["a","b","c"]`], `["a","b","c"]`);
});

test("array String[]: contains empty string", (t) => {
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "String[]", inPlace: false,
        params: [{ name: "a", type: "String[]" }],
    };
    _ok(t, code, meta, [`["","a","b"]`], `["","a","b"]`);
});

test("array twoSum: int[] + int → int[]", (t) => {
    const code = `
class Solution {
    twoSum(nums, target) {
        const seen = new Map();
        for (let i = 0; i < nums.length; i++) {
            const d = target - nums[i];
            if (seen.has(d)) return [seen.get(d), i];
            seen.set(nums[i], i);
        }
        return [];
    }
}`;
    const meta = {
        fn: "twoSum", return: "int[]", inPlace: false,
        params: [{ name: "nums", type: "int[]" }, { name: "target", type: "int" }],
    };
    _ok(t, code, meta, ["[2,7,11,15]", "9"], "[0,1]");
});

test("array longestCommonPrefix: String[] → String", (t) => {
    const code = `
class Solution {
    longestCommonPrefix(strs) {
        if (strs.length === 0) return "";
        let p = strs[0];
        for (let i = 1; i < strs.length; i++) {
            while (!strs[i].startsWith(p)) {
                p = p.slice(0, -1);
                if (p === "") return "";
            }
        }
        return p;
    }
}`;
    const meta = {
        fn: "longestCommonPrefix", return: "String", inPlace: false,
        params: [{ name: "strs", type: "String[]" }],
    };
    _ok(t, code, meta, [`["flower","flow","flight"]`], "fl");
    _ok(t, code, meta, [`["dog","racecar","car"]`], "");
    _ok(t, code, meta, [`[""]`], "");
});

// ── 2-D arrays ────────────────────────────────────────────────────────────

test("matrix int[][]: spiralOrder", (t) => {
    const code = `
class Solution {
    spiralOrder(m) {
        const res = [];
        while (m.length) {
            res.push(...m.shift());
            for (const r of m) { if (r.length) res.push(r.pop()); }
            if (m.length) { res.push(...m.pop().reverse()); }
            for (let i = m.length - 1; i >= 0; i--) {
                if (m[i].length) res.push(m[i].shift());
            }
        }
        return res;
    }
}`;
    const meta = {
        fn: "spiralOrder", return: "int[]", inPlace: false,
        params: [{ name: "m", type: "int[][]" }],
    };
    _ok(t, code, meta, ["[[1,2,3],[4,5,6],[7,8,9]]"], "[1,2,3,6,9,8,7,4,5]");
});

test("matrix int[][]: empty", (t) => {
    const code = "class Solution { echo(m) { return m; } }";
    const meta = {
        fn: "echo", return: "int[][]", inPlace: false,
        params: [{ name: "m", type: "int[][]" }],
    };
    _ok(t, code, meta, ["[]"], "[]");
});

test("matrix int[][]: 1x1", (t) => {
    const code = "class Solution { echo(m) { return m; } }";
    const meta = {
        fn: "echo", return: "int[][]", inPlace: false,
        params: [{ name: "m", type: "int[][]" }],
    };
    _ok(t, code, meta, ["[[42]]"], "[[42]]");
});

test("matrix char[][]: count 'X' on board", (t) => {
    const code = `
class Solution {
    countX(board) {
        let n = 0;
        for (const row of board) for (const c of row) if (c === "X") n++;
        return n;
    }
}`;
    const meta = {
        fn: "countX", return: "int", inPlace: false,
        params: [{ name: "board", type: "char[][]" }],
    };
    _ok(t, code, meta, [`[["X","O"],["X","X"]]`], "3");
});

test("matrix char[][]: word search exist", (t) => {
    const code = `
class Solution {
    exist(board, word) {
        const R = board.length, C = R ? board[0].length : 0;
        const dfs = (r, c, i) => {
            if (i === word.length) return true;
            if (r < 0 || r >= R || c < 0 || c >= C || board[r][c] !== word[i]) return false;
            const t = board[r][c]; board[r][c] = "#";
            const ok = dfs(r+1,c,i+1) || dfs(r-1,c,i+1) ||
                       dfs(r,c+1,i+1) || dfs(r,c-1,i+1);
            board[r][c] = t;
            return ok;
        };
        for (let r = 0; r < R; r++)
            for (let c = 0; c < C; c++)
                if (dfs(r, c, 0)) return true;
        return false;
    }
}`;
    const meta = {
        fn: "exist", return: "boolean", inPlace: false,
        params: [{ name: "board", type: "char[][]" }, { name: "word", type: "String" }],
    };
    const board = `[["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]]`;
    _ok(t, code, meta, [board, "ABCCED"], "true");
    _ok(t, code, meta, [board, "ABCB"], "false");
});

test("matrix String[][]: passthrough", (t) => {
    const code = "class Solution { echo(m) { return m; } }";
    const meta = {
        fn: "echo", return: "String[][]", inPlace: false,
        params: [{ name: "m", type: "String[][]" }],
    };
    _ok(t, code, meta, [`[["eat","tea"],["bat"]]`], `[["eat","tea"],["bat"]]`);
});

// ── List<...> ─────────────────────────────────────────────────────────────

test("list<integer>: doubleAll", (t) => {
    const code = "class Solution { doubleAll(xs) { return xs.map(x => x * 2); } }";
    const meta = {
        fn: "doubleAll", return: "List<Integer>", inPlace: false,
        params: [{ name: "xs", type: "List<Integer>" }],
    };
    _ok(t, code, meta, ["[1,2,3]"], "[2,4,6]");
});

test("list<integer>: empty", (t) => {
    const code = "class Solution { echo(xs) { return xs; } }";
    const meta = {
        fn: "echo", return: "List<Integer>", inPlace: false,
        params: [{ name: "xs", type: "List<Integer>" }],
    };
    _ok(t, code, meta, ["[]"], "[]");
});

test("list<string>: upper", (t) => {
    const code = "class Solution { upper(ws) { return ws.map(w => w.toUpperCase()); } }";
    const meta = {
        fn: "upper", return: "List<String>", inPlace: false,
        params: [{ name: "ws", type: "List<String>" }],
    };
    _ok(t, code, meta, [`["a","bc"]`], `["A","BC"]`);
});

test("list<list<integer>>: passthrough", (t) => {
    const code = "class Solution { echo(x) { return x; } }";
    const meta = {
        fn: "echo", return: "List<List<Integer>>", inPlace: false,
        params: [{ name: "x", type: "List<List<Integer>>" }],
    };
    _ok(t, code, meta, ["[[1,2],[3,4]]"], "[[1,2],[3,4]]");
});

test("list<list<integer>>: threeSum", (t) => {
    const code = `
class Solution {
    threeSum(nums) {
        nums.sort((a, b) => a - b);
        const res = [];
        for (let i = 0; i < nums.length - 2; i++) {
            if (i > 0 && nums[i] === nums[i-1]) continue;
            let l = i + 1, r = nums.length - 1;
            while (l < r) {
                const s = nums[i] + nums[l] + nums[r];
                if (s < 0) l++;
                else if (s > 0) r--;
                else {
                    res.push([nums[i], nums[l], nums[r]]);
                    while (l < r && nums[l] === nums[l+1]) l++;
                    while (l < r && nums[r] === nums[r-1]) r--;
                    l++; r--;
                }
            }
        }
        return res;
    }
}`;
    const meta = {
        fn: "threeSum", return: "List<List<Integer>>", inPlace: false,
        params: [{ name: "nums", type: "int[]" }],
    };
    _ok(t, code, meta, ["[-1,0,1,2,-1,-4]"], "[[-1,-1,2],[-1,0,1]]");
});

test("list<list<string>>: groupAnagrams (pre-sorted output)", (t) => {
    const code = `
class Solution {
    groupAnagrams(strs) {
        const m = new Map();
        for (const s of strs) {
            const k = s.split("").sort().join("");
            if (!m.has(k)) m.set(k, []);
            m.get(k).push(s);
        }
        // Sort within each group, then by group first element to make output deterministic.
        const groups = [];
        for (const v of m.values()) groups.push(v.slice().sort());
        groups.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
        return groups;
    }
}`;
    const meta = {
        fn: "groupAnagrams", return: "List<List<String>>", inPlace: false,
        params: [{ name: "strs", type: "String[]" }],
    };
    _ok(
        t, code, meta,
        [`["eat","tea","tan","ate","nat","bat"]`],
        `[["ate","eat","tea"],["bat"],["nat","tan"]]`
    );
});

// ── TreeNode ──────────────────────────────────────────────────────────────

test("tree: maxDepth full / empty / skewed", (t) => {
    const code = `
class Solution {
    maxDepth(root) {
        if (root === null) return 0;
        return 1 + Math.max(this.maxDepth(root.left), this.maxDepth(root.right));
    }
}`;
    const meta = {
        fn: "maxDepth", return: "int", inPlace: false,
        params: [{ name: "root", type: "TreeNode" }],
    };
    _ok(t, code, meta, ["[3,9,20,null,null,15,7]"], "3");
    _ok(t, code, meta, ["[]"], "0");
    _ok(t, code, meta, ["[1,null,2]"], "2");
});

test("tree: invertTree returns level-order", (t) => {
    const code = `
class Solution {
    invertTree(root) {
        if (root === null) return null;
        const t = root.left;
        root.left = this.invertTree(root.right);
        root.right = this.invertTree(t);
        return root;
    }
}`;
    const meta = {
        fn: "invertTree", return: "TreeNode", inPlace: false,
        params: [{ name: "root", type: "TreeNode" }],
    };
    _ok(t, code, meta, ["[4,2,7,1,3,6,9]"], "[4,7,2,9,6,3,1]");
});

test("tree: returning null serializes as []", (t) => {
    const code = "class Solution { f(root) { return null; } }";
    const meta = {
        fn: "f", return: "TreeNode", inPlace: false,
        params: [{ name: "root", type: "TreeNode" }],
    };
    _ok(t, code, meta, ["[1]"], "[]");
});

test("tree: empty input → null root → driver passes null", (t) => {
    const code = "class Solution { isNull(root) { return root === null; } }";
    const meta = {
        fn: "isNull", return: "boolean", inPlace: false,
        params: [{ name: "root", type: "TreeNode" }],
    };
    _ok(t, code, meta, ["[]"], "true");
});

test("tree: returning a freshly built TreeNode using driver class", (t) => {
    // User constructs TreeNode using the driver-provided class.
    const code = `
class Solution {
    f(root) {
        const r = new TreeNode(1);
        r.left = new TreeNode(2);
        r.right = new TreeNode(3);
        return r;
    }
}`;
    const meta = {
        fn: "f", return: "TreeNode", inPlace: false,
        params: [{ name: "root", type: "TreeNode" }],
    };
    _ok(t, code, meta, ["[]"], "[1,2,3]");
});

// ── ListNode ──────────────────────────────────────────────────────────────

test("list: reverseList non-empty", (t) => {
    const code = `
class Solution {
    reverseList(head) {
        let prev = null;
        while (head) {
            const n = head.next;
            head.next = prev;
            prev = head;
            head = n;
        }
        return prev;
    }
}`;
    const meta = {
        fn: "reverseList", return: "ListNode", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[1,2,3,4,5]"], "[5,4,3,2,1]");
});

test("list: reverseList empty", (t) => {
    const code = `
class Solution {
    reverseList(head) {
        let prev = null;
        while (head) {
            const n = head.next;
            head.next = prev;
            prev = head;
            head = n;
        }
        return prev;
    }
}`;
    const meta = {
        fn: "reverseList", return: "ListNode", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[]"], "[]");
});

test("list: ListNode input → int return (length)", (t) => {
    const code = `
class Solution {
    length(head) {
        let n = 0;
        while (head) { n++; head = head.next; }
        return n;
    }
}`;
    const meta = {
        fn: "length", return: "int", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[10,20,30]"], "3");
    _ok(t, code, meta, ["[]"], "0");
});

test("list: construct new ListNode using driver class", (t) => {
    const code = `
class Solution {
    doubleVals(head) {
        const dummy = new ListNode(0);
        let cur = dummy;
        while (head) {
            cur.next = new ListNode(head.val * 2);
            cur = cur.next;
            head = head.next;
        }
        return dummy.next;
    }
}`;
    const meta = {
        fn: "doubleVals", return: "ListNode", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[1,2,3]"], "[2,4,6]");
});

test("list: returning null serializes as []", (t) => {
    const code = "class Solution { empty(head) { return null; } }";
    const meta = {
        fn: "empty", return: "ListNode", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[1,2,3]"], "[]");
});

test("list: with negative values", (t) => {
    const code = `
class Solution {
    echo(head) {
        const dummy = new ListNode(0);
        let cur = dummy;
        while (head) {
            cur.next = new ListNode(head.val);
            cur = cur.next;
            head = head.next;
        }
        return dummy.next;
    }
}`;
    const meta = {
        fn: "echo", return: "ListNode", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[-1,0,1]"], "[-1,0,1]");
});

// ── inPlace ───────────────────────────────────────────────────────────────

test("inPlace: sortColors mutates first arg", (t) => {
    const code = "class Solution { sortColors(nums) { nums.sort((a, b) => a - b); } }";
    const meta = {
        fn: "sortColors", return: "void", inPlace: true,
        params: [{ name: "nums", type: "int[]" }],
    };
    _ok(t, code, meta, ["[2,0,2,1,1,0]"], "[0,0,1,1,2,2]");
});

test("inPlace: reverseString in place (String[])", (t) => {
    // void method that reverses a char array in-place; we use String[] for simplicity.
    const code = `
class Solution {
    reverseString(s) {
        s.reverse();
    }
}`;
    const meta = {
        fn: "reverseString", return: "void", inPlace: true,
        params: [{ name: "s", type: "String[]" }],
    };
    _ok(t, code, meta, [`["h","e","l","l","o"]`], `["o","l","l","e","h"]`);
});

// ── Edge cases & failure modes ────────────────────────────────────────────

test("free function entry point (LeetCode JS style) works", (t) => {
    // The starter code and the AI generator emit `var fn = function(){}` /
    // `function fn(){}`, NOT a class. The driver must accept that.
    const code = "function twoSum(nums, target) { return [nums.length, target]; }";
    const meta = {
        fn: "twoSum", return: "int[]", inPlace: false,
        params: [{ name: "nums", type: "int[]" }, { name: "target", type: "int" }],
    };
    _ok(t, code, meta, ["[1,2]", "3"], "[2,3]");
});

test("edge: neither free function nor Solution class → non-zero exit", (t) => {
    const code = "var unrelated = 1;";
    const meta = {
        fn: "twoSum", return: "int[]", inPlace: false,
        params: [{ name: "nums", type: "int[]" }, { name: "target", type: "int" }],
    };
    const { code: rc, stderr } = _run(code, meta, ["[1,2]", "3"]);
    assert.notEqual(rc, 0);
    assert.match(stderr, /twoSum/);
});

test("edge: method not on Solution → clear error", (t) => {
    const code = "class Solution { other() {} }";
    const meta = {
        fn: "twoSum", return: "int[]", inPlace: false,
        params: [{ name: "nums", type: "int[]" }],
    };
    const { code: rc, stderr } = _run(code, meta, ["[1,2]"]);
    assert.notEqual(rc, 0);
    assert.match(stderr, /twoSum/);
});

test("edge: top-level string preserves leading/trailing spaces", (t) => {
    // Per driver protocol, raw string params are NOT trimmed.
    const code = "class Solution { echo(s) { return s; } }";
    const meta = {
        fn: "echo", return: "String", inPlace: false,
        params: [{ name: "s", type: "String" }],
    };
    _ok(t, code, meta, ["  hi  "], "  hi  ");
});

test("edge: orderMatters flag is parsed without breaking driver", (t) => {
    // The driver itself doesn't act on orderMatters — it only matters for the
    // OutputComparator. But functionMeta carries the flag, so make sure parsing
    // a meta with orderMatters=true doesn't blow up.
    const code = "class Solution { echo(a) { return a; } }";
    const meta = {
        fn: "echo", return: "int[]", inPlace: false, orderMatters: true,
        params: [{ name: "a", type: "int[]" }],
    };
    _ok(t, code, meta, ["[1,2,3]"], "[1,2,3]");
});

test("edge: meta with extra unknown fields is tolerated", (t) => {
    const code = "class Solution { echo(n) { return n; } }";
    const meta = {
        fn: "echo", return: "int", inPlace: false,
        params: [{ name: "n", type: "int" }],
        // extra fields the driver should ignore
        difficulty: "easy",
        notes: "ignore me",
    };
    _ok(t, code, meta, ["7"], "7");
});

test("edge: head passed to user code is an instance of the driver-provided ListNode", (t) => {
    // JS contract: TreeNode / ListNode are pre-declared by the driver via `class`,
    // so user code MUST NOT redeclare them at the top level (would be a SyntaxError —
    // `class` declarations are lexically scoped like `let`). The flip side is that
    // `head instanceof ListNode` always holds inside user code, which lets users
    // assert the driver wired things up correctly.
    const code = `
class Solution {
    check(head) {
        if (head === null) return -1;
        return head instanceof ListNode ? head.val : -2;
    }
}`;
    const meta = {
        fn: "check", return: "int", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    _ok(t, code, meta, ["[42, 7]"], "42");
    _ok(t, code, meta, ["[]"], "-1");
});

test("edge: redeclaring ListNode in user code is a SyntaxError (documented contract)", (t) => {
    // Documents the limitation: users cannot redeclare TreeNode/ListNode.
    // The driver pre-declares them with `class`, which has lexical scope.
    const code = `
class ListNode { constructor(v) { this.val = v; this.next = null; } }
class Solution { f(head) { return 0; } }`;
    const meta = {
        fn: "f", return: "int", inPlace: false,
        params: [{ name: "head", type: "ListNode" }],
    };
    const { code: rc, stderr } = _run(code, meta, ["[1]"]);
    assert.notEqual(rc, 0);
    assert.match(stderr, /already been declared|SyntaxError/);
});
