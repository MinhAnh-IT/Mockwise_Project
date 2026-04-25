# Judge Service — Integration Guide

This guide covers everything you need to submit code for evaluation: request schema, supported types, naming rules, input/output format per type, flag behaviour, and known limitations.

---

## Table of Contents

1. [How It Works](#1-how-it-works)
2. [Submission Request Schema](#2-submission-request-schema)
   - 2.1 [Language: Java vs Python](#21-language-java-vs-python)
3. [functionMeta Reference](#3-functionmeta-reference)
4. [Naming Conventions](#4-naming-conventions)
5. [Supported Types](#5-supported-types)
   - 5.1 [Primitives — int, long, double, boolean](#51-primitives--int-long-double-boolean)
   - 5.2 [String](#52-string)
   - 5.3 [int\[\] / long\[\] / double\[\]](#53-int--long--double)
   - 5.4 [String\[\]](#54-string)
   - 5.5 [int\[\]\[\]](#55-int)
   - 5.6 [char\[\]\[\]](#56-char)
   - 5.7 [TreeNode](#57-treenode)
   - 5.8 [ListNode](#58-listnode)
   - 5.9 [char](#59-char)
   - 5.10 [String\[\]\[\]](#510-string)
   - 5.11 [List\<Integer\>](#511-listinteger)
   - 5.12 [List\<String\>](#512-liststring)
   - 5.13 [List\<List\<Integer\>\>](#513-listlistinteger)
   - 5.14 [List\<List\<String\>\>](#514-listliststring)
6. [Flags: orderMatters & inPlace](#6-flags-ordermatters--inplace)
7. [Verdict Types](#7-verdict-types)
8. [Edge Cases & Constraints Per Type](#8-edge-cases--constraints-per-type)
9. [Unsupported Types](#9-unsupported-types)

---

## 1. How It Works

```
Client sends SubmissionEvent (via Kafka or REST)
  │
  ├─ StdinBuilder       serialize inputData  → stdin lines for Judge0
  ├─ Judge0             compile & run the code
  ├─ OutputComparator   compare stdout vs expectedOutput
  └─ JudgeResultEvent   publish verdict per test case
```

A language-specific driver (`UniversalJavaDriver` or `UniversalPythonDriver`) running inside Judge0:
- Reads `functionMeta` from **stdin line 1** (JSON)
- Reads each param value from the subsequent stdin lines
- Calls `Solution.<fn>(args...)` (Java reflection / Python `getattr`)
- Prints the return value (or first argument if `inPlace: true`) as JSON to stdout

The stdin format and the output format are identical across languages — switching `language` only changes which driver wraps the user code.

---

## 2. Submission Request Schema

```json
{
  "submissionId": "UUID",
  "language": "java",
  "code": "<class Solution { ... }>",
  "functionMeta": { ... },
  "testCases": [ ... ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `submissionId` | UUID string | yes | Unique ID for this submission |
| `language` | string | yes | `"java"` or `"python"` — see [§2.1](#21-language-java-vs-python) |
| `code` | string | yes | The solution body — must contain a class named `Solution` |
| `functionMeta` | object | yes | Describes the function to call — see section 3 |
| `testCases` | array | yes | One or more test case objects — see section 3 |

### 2.1 Language: Java vs Python

Both languages use the same `functionMeta`, `inputData`, and `expectedOutput` shape. The only difference is how user code is structured.

**Java:**
- Must contain `class Solution { ... }`. The judge wraps it; do **not** declare it `public`.
- The target method is an **instance** method on `Solution`. The driver creates `new Solution()` and invokes via reflection.
- Use boxed types in lists (`List<Integer>`, `List<String>`); the driver auto-normalizes the `params[].type` strings.

```java
class Solution {
    public int[] twoSum(int[] nums, int target) {
        // ...
    }
}
```

**Python:**
- Must contain `class Solution:` with the target method defined as `def <fn>(self, ...):`.
- The driver instantiates `Solution()` and dispatches via `getattr`.
- Type strings in `functionMeta.params[].type` stay the same (`int[]`, `String`, `List<Integer>`, `TreeNode`, etc.) — they describe the **schema**, not the user-language type. The Python driver maps them to native Python values: `int[]` / `List<Integer>` → `list[int]`, `String[]` → `list[str]`, `char[][]` → `list[list[str]]` (each inner item is a 1-char string), etc.
- `TreeNode` and `ListNode` classes are pre-defined by the driver and visible to user code.

```python
class Solution:
    def twoSum(self, nums, target):
        seen = {}
        for i, n in enumerate(nums):
            d = target - n
            if d in seen:
                return [seen[d], i]
            seen[n] = i
        return []
```

**Output equivalence.** Both drivers emit the exact same stdout for a given test case (e.g. `[0,1]`, `true`, `hello` — top-level strings are unquoted). `OutputComparator` parses stdout as JSON; languages that produce identical JSON pass identically.

**Python boolean note.** The driver detects `bool` before `int` (`isinstance(True, int) == True` in Python is a known gotcha) and prints `true` / `false` to match Java.

**Python char handling.** Python has no `char` primitive. A `char` param is delivered as a 1-char `str`; a `char` return must also be a 1-char `str`. A `char[][]` param is `list[list[str]]` where every inner element is exactly one character.

### testCases item schema

```json
{
  "id": "UUID",
  "inputData": {
    "<paramName>": <value>
  },
  "expectedOutput": {
    "result": <value>
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | UUID string | yes | Unique ID for this test case |
| `inputData` | object | yes | Keys are param names from `functionMeta.params`, values match the declared types |
| `expectedOutput` | object | yes | Must have exactly one key; the key name is arbitrary but **must be consistent** (conventionally `"result"`). The comparator reads the first field of this object. |

> **Rule:** `expectedOutput` must be a JSON object with exactly one field. The comparator takes the first field regardless of the key name.
> Correct: `{"result": 42}` or `{"answer": 42}`
> Wrong: `42` or `[0, 1]` (bare values without a wrapper object)

---

## 3. functionMeta Reference

```json
{
  "fn": "methodName",
  "params": [
    { "name": "paramName", "type": "typeString" }
  ],
  "return": "typeString",
  "orderMatters": false,
  "inPlace": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `fn` | string | yes | Exact name of the method in `Solution` to call |
| `params` | array | yes | Ordered list of parameters — order must match the Java method signature |
| `params[].name` | string | yes | Used as the key to look up the value in `inputData` |
| `params[].type` | string | yes | Type string — see [Supported Types](#5-supported-types) for valid values |
| `return` | string | yes | Return type string. Required even for `void` methods — set to `"void"` |
| `orderMatters` | boolean | yes | If `false` and the output is an array, elements are sorted before comparison |
| `inPlace` | boolean | yes | If `true`, the first argument is used as the output instead of the return value |

---

## 4. Naming Conventions

### `fn`
- Must exactly match the Java method name in `Solution` (case-sensitive)
- Example: `"fn": "twoSum"` → the method must be `public ... twoSum(...)`

### `params[].name`
- Must exactly match the key used in `inputData`
- Example: `{"name": "nums"}` → `inputData` must have `"nums": [...]`

### `params[].type` — valid values

| Category | Valid type strings |
|----------|--------------------|
| Primitive | `int`, `long`, `double`, `boolean` |
| Boxed | `Integer`, `Long`, `Double`, `Boolean` (auto-normalized) |
| Character | `char`, `Character` (auto-normalized) |
| String | `String`, `string` |
| 1-D array | `int[]`, `long[]`, `double[]`, `String[]`, `string[]` |
| 2-D array | `int[][]`, `char[][]`, `String[][]` |
| List | `List<Integer>`, `List<String>`, `List<List<Integer>>`, `List<List<String>>` |
| Tree | `TreeNode` |
| Linked list | `ListNode` |

### `return` — valid values
Same as `params[].type`, plus `void` for in-place mutations.

### `expectedOutput` key
- Must be a JSON object with exactly **one** field
- Conventionally use `"result"` as the key name
- The comparator always picks the **first** field of the object

---

## 5. Supported Types

### 5.1 Primitives — int, long, double, boolean

**In `inputData`:** plain JSON number or boolean

**In `expectedOutput`:** plain JSON number or boolean wrapped in an object

**Type string aliases:** `Integer` → `int`, `Long` → `long`, `Double` → `double`, `Boolean` → `boolean`

| Scenario | `inputData` | `expectedOutput` | Notes |
|----------|-------------|------------------|-------|
| Positive | `{"n": 42}` | `{"result": 42}` | |
| Negative | `{"n": -7}` | `{"result": -7}` | |
| Zero | `{"n": 0}` | `{"result": 0}` | |
| MAX_INT | `{"n": 2147483647}` | `{"result": 2147483647}` | Boundary |
| MIN_INT | `{"n": -2147483648}` | `{"result": -2147483648}` | Boundary |
| Long large | `{"n": 9223372036854775807}` | `{"result": 9223372036854775807}` | |
| Double | `{"x": 3.14}` | `{"result": 3.14}` | Exact comparison |
| Boolean | `{"flag": true}` | `{"result": true}` | |

**Example:**
```json
{
  "language": "java",
  "code": "public class Solution { public int add(int a, int b) { return a + b; } }",
  "functionMeta": {
    "fn": "add",
    "params": [{"name": "a", "type": "int"}, {"name": "b", "type": "int"}],
    "return": "int",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"a": 3,  "b": 5},  "expectedOutput": {"result": 8}},
    {"id": "...", "inputData": {"a": -1, "b": 1},  "expectedOutput": {"result": 0}},
    {"id": "...", "inputData": {"a": 0,  "b": 0},  "expectedOutput": {"result": 0}}
  ]
}
```

> **double precision:** comparison is exact (`JsonNode.equals`). Do not use `double` for problems where floating-point rounding matters (e.g., `0.1 + 0.2 = 0.30000000000000004`). See [Unsupported Types](#9-unsupported-types).

---

### 5.2 String

**Type string:** `String` or `string` (identical behaviour)

**In `inputData`:** plain JSON string — `"hello"`

**In `expectedOutput`:** JSON string wrapped in object — `{"result": "hello"}`

**Driver behaviour:** the string is passed to the function without surrounding quotes. Return value is printed as-is (no quotes added).

| Scenario | `inputData` | `expectedOutput` | Notes |
|----------|-------------|------------------|-------|
| Normal | `{"s": "hello"}` | `{"result": "hello"}` | |
| Empty | `{"s": ""}` | `{"result": ""}` | Blank stdout — handled correctly |
| Single char | `{"s": "a"}` | `{"result": "a"}` | |
| With spaces | `{"s": "hello world"}` | `{"result": "hello world"}` | |
| Special chars | `{"s": "a+b=c"}` | `{"result": "a+b=c"}` | |
| Brackets | `{"s": "()[]{}"}` | `{"result": true}` | Return boolean |

**Example (isValid — String param, boolean return):**
```json
{
  "language": "java",
  "code": "public class Solution { public boolean isValid(String s) { ... } }",
  "functionMeta": {
    "fn": "isValid",
    "params": [{"name": "s", "type": "String"}],
    "return": "boolean",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"s": "()"},     "expectedOutput": {"result": true}},
    {"id": "...", "inputData": {"s": "()[]{}"},  "expectedOutput": {"result": true}},
    {"id": "...", "inputData": {"s": "(]"},      "expectedOutput": {"result": false}},
    {"id": "...", "inputData": {"s": ""},        "expectedOutput": {"result": true}}
  ]
}
```

---

### 5.3 int[] / long[] / double[]

**In `inputData`:** JSON number array — `[1, 2, 3]`

**In `expectedOutput`:** JSON number array wrapped in object — `{"result": [0, 1]}`

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"nums": [2, 7, 11, 15]}` | |
| Empty array | `{"nums": []}` | Must be handled by the solution |
| Single element | `{"nums": [42]}` | |
| All negatives | `{"nums": [-5, -3, -1]}` | |
| With duplicates | `{"nums": [1, 1, 2, 2]}` | |
| long boundary | `{"nums": [9000000000, 1]}` | Use `long[]` type |

**Example (twoSum — int[] param, int param, int[] return, orderMatters: false):**
```json
{
  "language": "java",
  "code": "public class Solution { public int[] twoSum(int[] nums, int target) { ... } }",
  "functionMeta": {
    "fn": "twoSum",
    "params": [
      {"name": "nums",   "type": "int[]"},
      {"name": "target", "type": "int"}
    ],
    "return": "int[]",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"nums": [2, 7, 11, 15], "target": 9}, "expectedOutput": {"result": [0, 1]}},
    {"id": "...", "inputData": {"nums": [3, 2, 4],      "target": 6}, "expectedOutput": {"result": [1, 2]}},
    {"id": "...", "inputData": {"nums": [3, 3],          "target": 6}, "expectedOutput": {"result": [0, 1]}}
  ]
}
```

> `orderMatters: false` — `[1, 0]` and `[0, 1]` are both accepted as correct.

---

### 5.4 String[]

**Type string:** `String[]` or `string[]` (identical)

**In `inputData`:** JSON string array — `["a", "b", "c"]`

**In `expectedOutput`:** JSON string array or string, wrapped in object

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"strs": ["flower", "flow", "flight"]}` | |
| Empty array | `{"strs": []}` | |
| Single element | `{"strs": ["hello"]}` | |
| Contains empty string | `{"strs": ["", "a", "b"]}` | Common in prefix problems |
| All identical | `{"strs": ["abc", "abc"]}` | |

**Example (longestCommonPrefix — String[] param, String return):**
```json
{
  "language": "java",
  "code": "public class Solution { public String longestCommonPrefix(String[] strs) { ... } }",
  "functionMeta": {
    "fn": "longestCommonPrefix",
    "params": [{"name": "strs", "type": "String[]"}],
    "return": "String",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"strs": ["flower", "flow", "flight"]}, "expectedOutput": {"result": "fl"}},
    {"id": "...", "inputData": {"strs": ["dog", "racecar", "car"]},    "expectedOutput": {"result": ""}},
    {"id": "...", "inputData": {"strs": [""]},                         "expectedOutput": {"result": ""}}
  ]
}
```

---

### 5.5 int[][]

**In `inputData`:** nested JSON number array — `[[1,2,3],[4,5,6]]`

**In `expectedOutput`:** nested JSON array or flat array, wrapped in object

**Constraint:** all rows must have the same length (jagged arrays are not supported).

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Square 3×3 | `{"matrix": [[1,2,3],[4,5,6],[7,8,9]]}` | |
| 1×1 | `{"matrix": [[42]]}` | |
| Empty | `{"matrix": []}` | |
| 1×N (single row) | `{"matrix": [[1,2,3]]}` | |
| N×1 (single column) | `{"matrix": [[1],[2],[3]]}` | |
| Non-square M×N | `{"matrix": [[1,2,3],[4,5,6]]}` | 2×3 — supported |
| With negatives | `{"matrix": [[-1,2],[-3,4]]}` | |

**Example (spiralOrder — int[][] param, int[] return, orderMatters: true):**
```json
{
  "language": "java",
  "code": "public class Solution { public int[] spiralOrder(int[][] matrix) { ... } }",
  "functionMeta": {
    "fn": "spiralOrder",
    "params": [{"name": "matrix", "type": "int[][]"}],
    "return": "int[]",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData": {"matrix": [[1,2,3],[4,5,6],[7,8,9]]},
      "expectedOutput": {"result": [1,2,3,6,9,8,7,4,5]}
    },
    {
      "id": "...",
      "inputData": {"matrix": [[1,2],[3,4]]},
      "expectedOutput": {"result": [1,2,4,3]}
    }
  ]
}
```

---

### 5.6 char[][]

**In `inputData`:** nested JSON string array where each element is a **single character** — `[["A","B"],["C","D"]]`

**In `expectedOutput`:** depends on return type — boolean, int, or nested string array

**Constraint:** each string element must be exactly 1 character.

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Word search board | `{"board": [["A","B","C"],["D","E","F"]]}` | |
| Sudoku board | `{"board": [["5","3","."],[...]]}` | Digits and '.' as chars |
| BFS/DFS grid | `{"board": [["1","0","1"],["0","1","0"]]}` | |
| 1×1 | `{"board": [["X"]]}` | |
| Empty | `{"board": []}` | |

**Example (exist — char[][] + String params, boolean return):**
```json
{
  "language": "java",
  "code": "public class Solution { public boolean exist(char[][] board, String word) { ... } }",
  "functionMeta": {
    "fn": "exist",
    "params": [
      {"name": "board", "type": "char[][]"},
      {"name": "word",  "type": "String"}
    ],
    "return": "boolean",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData": {
        "board": [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]],
        "word": "ABCCED"
      },
      "expectedOutput": {"result": true}
    },
    {
      "id": "...",
      "inputData": {
        "board": [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]],
        "word": "ABCB"
      },
      "expectedOutput": {"result": false}
    }
  ]
}
```

---

### 5.7 TreeNode

**Representation:** level-order (BFS) array with `null` marking absent nodes.

**In `inputData`:** JSON array of numbers and nulls — `[3, 9, 20, null, null, 15, 7]`

**In `expectedOutput`:** same level-order format for TreeNode return, or a scalar/array for other return types

**Empty tree:** use `[]` — the driver builds a `null` root from an empty array.

**TreeNode return value:** the driver serializes the result tree back to level-order, stripping trailing nulls. So `[1, null, 2, null, null, null, 3]` is trimmed to `[1, null, 2, null, null, null, 3]` — only trailing nulls after the last real node are removed.

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Full tree | `{"root": [3,9,20,null,null,15,7]}` | Both children present at last level |
| Single node | `{"root": [1]}` | |
| Empty tree | `{"root": []}` | root is null |
| Left-skewed | `{"root": [1,2,null,3,null]}` | Only left children |
| Right-skewed | `{"root": [1,null,2,null,3]}` | Only right children |
| With gap | `{"root": [1,null,2]}` | Missing left child |
| BST | `{"root": [5,3,7,1,4,6,8]}` | All nodes present |

**Example (maxDepth — TreeNode param, int return):**
```json
{
  "language": "java",
  "code": "public class Solution { public int maxDepth(TreeNode root) { ... } }",
  "functionMeta": {
    "fn": "maxDepth",
    "params": [{"name": "root", "type": "TreeNode"}],
    "return": "int",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"root": [3,9,20,null,null,15,7]}, "expectedOutput": {"result": 3}},
    {"id": "...", "inputData": {"root": [1,null,2]},              "expectedOutput": {"result": 2}},
    {"id": "...", "inputData": {"root": []},                      "expectedOutput": {"result": 0}}
  ]
}
```

**Example (returning a TreeNode — invertTree):**
```json
{
  "functionMeta": {
    "fn": "invertTree",
    "params": [{"name": "root", "type": "TreeNode"}],
    "return": "TreeNode",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData":      {"root": [4,2,7,1,3,6,9]},
      "expectedOutput": {"result": [4,7,2,9,6,3,1]}
    },
    {
      "id": "...",
      "inputData":      {"root": []},
      "expectedOutput": {"result": []}
    }
  ]
}
```

---

### 5.8 ListNode

**Representation:** flat JSON integer array, in node order from head to tail.

**In `inputData`:** JSON int array — `[1, 2, 3, 4, 5]`

**In `expectedOutput`:** JSON int array wrapped in object

**Empty list:** use `[]` — the driver builds a `null` head from an empty array. The driver correctly prints `[]` for a null return value (not `"null"`).

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"head": [1,2,3,4,5]}` | |
| Single node | `{"head": [1]}` | |
| Empty list | `{"head": []}` | head is null |
| Even length | `{"head": [1,2,3,4]}` | Useful for middle-node problems |
| Odd length | `{"head": [1,2,3]}` | |
| With negatives | `{"head": [-1,0,1]}` | |
| All same | `{"head": [3,3,3]}` | Useful for duplicate-removal problems |

**Example (reverseList — ListNode param and return):**
```json
{
  "language": "java",
  "code": "public class Solution { public ListNode reverseList(ListNode head) { ... } }",
  "functionMeta": {
    "fn": "reverseList",
    "params": [{"name": "head", "type": "ListNode"}],
    "return": "ListNode",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"head": [1,2,3,4,5]}, "expectedOutput": {"result": [5,4,3,2,1]}},
    {"id": "...", "inputData": {"head": [1,2]},        "expectedOutput": {"result": [2,1]}},
    {"id": "...", "inputData": {"head": []},            "expectedOutput": {"result": []}}
  ]
}
```

---

### 5.9 char

**Type string:** `char` or `Character` (auto-normalized)

**In `inputData`:** a single-character JSON string — `"A"`

**In `expectedOutput`:** a single-character JSON string wrapped in object — `{"result": "A"}`

**Driver behaviour:** the char is read from stdin as the raw string (no quotes). Passed to the method as a `char` primitive.

| Scenario | `inputData` | `expectedOutput` | Notes |
|----------|-------------|------------------|-------|
| Letter | `{"c": "A"}` | `{"result": "A"}` | |
| Digit char | `{"c": "3"}` | `{"result": "3"}` | |
| Space | `{"c": " "}` | `{"result": " "}` | |

**Example (findTheDifference — two String params, char return):**
```json
{
  "language": "java",
  "code": "public class Solution { public char findTheDifference(String s, String t) { ... } }",
  "functionMeta": {
    "fn": "findTheDifference",
    "params": [
      {"name": "s", "type": "String"},
      {"name": "t", "type": "String"}
    ],
    "return": "char",
    "orderMatters": true,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"s": "abcd", "t": "abcde"}, "expectedOutput": {"result": "e"}},
    {"id": "...", "inputData": {"s": "",     "t": "y"},     "expectedOutput": {"result": "y"}}
  ]
}
```

> **char output:** The driver prints the character without quotes (e.g., `A`). `OutputComparator` falls back to string comparison, so `"A"` in `expectedOutput` matches the stdout `A`.

---

### 5.10 String[][]

**Type string:** `String[][]`

**In `inputData`:** nested JSON string array — `[["eat","tea"],["bat"],["tan","nat"]]`

**In `expectedOutput`:** nested JSON string array wrapped in object

**Constraint:** each inner array element must be a valid JSON string.

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"groups": [["eat","tea"],["bat"]]}` | |
| Empty outer | `{"groups": []}` | |
| Single group | `{"groups": [["abc"]]}` | |

**Example (groupAnagrams — String[] param, List<List<String>> return — also see 5.14):**
```json
{
  "language": "java",
  "code": "public class Solution { public String[][] groupAnagrams(String[][] data) { ... } }",
  "functionMeta": {
    "fn": "groupAnagrams",
    "params": [{"name": "data", "type": "String[][]"}],
    "return": "String[][]",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData": {"data": [["eat","tea"],["bat"],["tan","nat"]]},
      "expectedOutput": {"result": [["eat","tea"],["bat"],["nat","tan"]]}
    }
  ]
}
```

---

### 5.11 List\<Integer\>

**Type string:** `List<Integer>`

**In `inputData`:** JSON number array — `[1, 2, 3]`

**In `expectedOutput`:** JSON number array wrapped in object — `{"result": [1, 2, 3]}`

**Driver behaviour:** parsed as `ArrayList<Integer>`. Passed to the method as `List` (type erasure). Return value serialized as a JSON number array.

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"nums": [1, 2, 3]}` | |
| Empty | `{"nums": []}` | |
| Negatives | `{"nums": [-3, -1, 0, 1]}` | |

**Example:**
```json
{
  "language": "java",
  "code": "public class Solution { public List<Integer> twoSumList(List<Integer> nums, int target) { ... } }",
  "functionMeta": {
    "fn": "twoSumList",
    "params": [
      {"name": "nums",   "type": "List<Integer>"},
      {"name": "target", "type": "int"}
    ],
    "return": "List<Integer>",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"nums": [2, 7, 11, 15], "target": 9}, "expectedOutput": {"result": [0, 1]}}
  ]
}
```

---

### 5.12 List\<String\>

**Type string:** `List<String>`

**In `inputData`:** JSON string array — `["eat", "tea", "bat"]`

**In `expectedOutput`:** JSON string array wrapped in object — `{"result": ["eat", "tea"]}`

**Constraint:** each element must be a valid JSON string (with quotes). Unquoted values like `[eat,tea]` will not match.

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"words": ["eat", "tea", "bat"]}` | |
| Empty | `{"words": []}` | |
| Single element | `{"words": ["hello"]}` | |

**Example:**
```json
{
  "language": "java",
  "code": "public class Solution { public List<String> filterWords(List<String> words, String prefix) { ... } }",
  "functionMeta": {
    "fn": "filterWords",
    "params": [
      {"name": "words",  "type": "List<String>"},
      {"name": "prefix", "type": "String"}
    ],
    "return": "List<String>",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {"id": "...", "inputData": {"words": ["eat", "tea", "bat"], "prefix": "ea"}, "expectedOutput": {"result": ["eat"]}}
  ]
}
```

---

### 5.13 List\<List\<Integer\>\>

**Type string:** `List<List<Integer>>`

**In `inputData`:** nested JSON number array — `[[0,1],[2,3]]`

**In `expectedOutput`:** nested JSON number array wrapped in object

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"pairs": [[0,1],[2,3]]}` | |
| Empty outer | `{"pairs": []}` | |
| Variable inner sizes | `{"pairs": [[1],[2,3],[4,5,6]]}` | |

**Example (threeSum — int[] param, List<List<Integer>> return):**
```json
{
  "language": "java",
  "code": "public class Solution { public List<List<Integer>> threeSum(int[] nums) { ... } }",
  "functionMeta": {
    "fn": "threeSum",
    "params": [{"name": "nums", "type": "int[]"}],
    "return": "List<List<Integer>>",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData": {"nums": [-1, 0, 1, 2, -1, -4]},
      "expectedOutput": {"result": [[-1,-1,2],[-1,0,1]]}
    },
    {
      "id": "...",
      "inputData": {"nums": [0, 0, 0]},
      "expectedOutput": {"result": [[0,0,0]]}
    }
  ]
}
```

> `orderMatters: false` sorts the outer list. Inner lists are compared as-is. Pre-sort inner lists in `expectedOutput` if they may appear in different orders.

---

### 5.14 List\<List\<String\>\>

**Type string:** `List<List<String>>`

**In `inputData`:** nested JSON string array — `[["eat","tea"],["bat"],["tan","nat"]]`

**In `expectedOutput`:** nested JSON string array wrapped in object

**Constraint:** each inner element must be a valid JSON string (with quotes).

| Scenario | `inputData` | Notes |
|----------|-------------|-------|
| Normal | `{"groups": [["eat","tea"],["bat"]]}` | |
| Empty outer | `{"groups": []}` | |
| Variable inner sizes | `{"groups": [["a"],["b","c","d"]]}` | |

**Example (groupAnagrams — String[] param, List<List<String>> return):**
```json
{
  "language": "java",
  "code": "public class Solution { public List<List<String>> groupAnagrams(String[] strs) { ... } }",
  "functionMeta": {
    "fn": "groupAnagrams",
    "params": [{"name": "strs", "type": "String[]"}],
    "return": "List<List<String>>",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "...",
      "inputData": {"strs": ["eat","tea","tan","ate","nat","bat"]},
      "expectedOutput": {"result": [["bat"],["nat","tan"],["ate","eat","tea"]]}
    }
  ]
}
```

> `orderMatters: false` sorts outer groups by their JSON representation. Inner lists are compared as-is — pre-sort elements within each group in `expectedOutput`.

---

## 6. Flags: orderMatters & inPlace

### `orderMatters`

Controls how array output is compared.

| Value | Behaviour |
|-------|-----------|
| `true` | Exact element order required. `[0,1]` ≠ `[1,0]`. |
| `false` | Both arrays are sorted before comparison. `[0,1]` = `[1,0]`. |

`orderMatters` only affects **top-level arrays**. For nested arrays (e.g., `List<List<Integer>>`), the outer list is sorted by `JsonNode.toString()`, but the inner lists are **not** re-sorted.

| stdout | expectedOutput | orderMatters | Result |
|--------|----------------|--------------|--------|
| `[1,0]` | `{"result":[0,1]}` | `false` | AC |
| `[1,0]` | `{"result":[0,1]}` | `true` | WA |
| `[[3,4],[1,2]]` | `{"result":[[1,2],[3,4]]}` | `false` | AC (outer sorted) |
| `[[2,1],[3,4]]` | `{"result":[[1,2],[3,4]]}` | `false` | WA (inner not sorted) |

**When to use `false`:** twoSum, groupAnagrams, permutations — any problem where the answer set is correct regardless of order.

**When to use `true`:** isValid, spiralOrder, reverseList, longestCommonPrefix — any problem where output sequence is meaningful.

---

### `inPlace`

Controls what value is used as the output.

| Value | Output source |
|-------|---------------|
| `false` | The return value of the method |
| `true` | The value of `params[0]` (first argument) after the method returns |

Use `inPlace: true` for `void` methods that mutate their first argument (e.g., `sortColors`, `reverseString`, `rotate`).

Set `"return": "void"` when using `inPlace: true`.

**Example (sortColors — void, inPlace: true):**
```json
{
  "language": "java",
  "code": "public class Solution { public void sortColors(int[] nums) { ... } }",
  "functionMeta": {
    "fn": "sortColors",
    "params": [{"name": "nums", "type": "int[]"}],
    "return": "void",
    "orderMatters": true,
    "inPlace": true
  },
  "testCases": [
    {"id": "...", "inputData": {"nums": [2,0,2,1,1,0]}, "expectedOutput": {"result": [0,0,1,1,2,2]}},
    {"id": "...", "inputData": {"nums": [2,0,1]},        "expectedOutput": {"result": [0,1,2]}}
  ]
}
```

---

## 7. Verdict Types

| Verdict | Meaning |
|---------|---------|
| `AC` | All test cases accepted |
| `WA` | At least one test case produced wrong output |
| `CE` | Compilation error — `stderr` contains the compiler message |
| `RE` | Runtime error — uncaught exception during execution |
| `TLE` | Time limit exceeded |
| `MLE` | Memory limit exceeded |

The overall job verdict is the worst result across all test cases: `CE` > `RE` > `TLE` > `MLE` > `WA` > `AC`.

---

## 8. Edge Cases & Constraints Per Type

| Type | Empty input | Null return | Boundary |
|------|:-----------:|:-----------:|----------|
| `int` | N/A | N/A | `±2147483647` |
| `long` | N/A | N/A | `±9223372036854775807` |
| `double` | N/A | N/A | Exact comparison — avoid float math results |
| `boolean` | N/A | N/A | Only `true` / `false` |
| `char` | N/A | N/A | Single character — no surrounding quotes in stdin |
| `String` | `""` → blank stdout, matches `{"result":""}` | N/A | |
| `int[]` | `[]` → `[]` stdout | N/A | |
| `long[]` | `[]` → `[]` stdout | N/A | |
| `double[]` | `[]` → `[]` stdout | N/A | |
| `String[]` | `[]` → `[]` stdout | N/A | |
| `int[][]` | `[]` → `[]` stdout | N/A | All rows must have equal length |
| `char[][]` | `[]` → `[]` stdout | N/A | Each element must be a 1-char string |
| `String[][]` | `[]` → `[]` stdout | N/A | Each element is a JSON string |
| `List<Integer>` | `[]` → `[]` stdout | N/A | |
| `List<String>` | `[]` → `[]` stdout | N/A | Elements must be quoted JSON strings |
| `List<List<Integer>>` | `[]` → `[]` stdout | N/A | |
| `List<List<String>>` | `[]` → `[]` stdout | N/A | Inner elements must be quoted JSON strings |
| `TreeNode` | `[]` input → null root | null return → `[]` stdout | Deeply skewed trees may cause stack overflow |
| `ListNode` | `[]` input → null head | null return → `[]` stdout | Only `int` values supported |

---

## 9. Unsupported Types

The limitations below fall into three categories depending on which side of the pipeline they affect:

- **Input** — the type string in `params[].type`. Failure happens when the driver tries to parse the stdin value or look up the method via reflection.
- **Output** — the type string in `return`. Failure happens when the driver tries to serialize the return value, or when `OutputComparator` compares it.
- **Both** — the type string is invalid in either position.

### 9.1 Unsupported as Input (`params[].type`)

| Type string | What happens | Workaround |
|-------------|--------------|------------|
| `Map<K, V>` | No parser in driver (`parseValue` hits the default `return raw` branch) → the map is passed as a raw string → `RE` or wrong result | Not available yet |

### 9.2 Unsupported as Output (`return`)

| Type string | What happens | Workaround |
|-------------|--------------|------------|
| `Map<K, V>` | `toJson()` falls through to `String.valueOf()` → `{key=value}` format, not JSON → `WA` | Not available yet |

### 9.3 Comparison Limitations (affects `expectedOutput` accuracy)

These are not type errors — the submission runs and produces output, but the comparison logic has known edge cases.

| Scenario | What happens | Workaround |
|----------|--------------|------------|
| `double` return with floating-point arithmetic | Comparison is exact (`JsonNode.equals`). `0.1 + 0.2` produces `0.30000000000000004`, which does not equal `0.3` | Set `expectedOutput` to the exact floating-point result, or avoid problems that rely on float precision |
| Nested `List<List<>>` with `orderMatters: false` | Only the **outer** list is sorted before comparison. Inner lists are compared as-is. `[[2,1],[3,4]]` ≠ `[[1,2],[3,4]]` even with `orderMatters: false` | Pre-sort each inner list in the `expectedOutput`, or sort inside the solution before returning |
