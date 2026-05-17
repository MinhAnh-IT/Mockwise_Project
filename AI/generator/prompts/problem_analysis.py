from typing import Optional

from models.generator_inputs import GenerateTestcasesRequest


def build_problem_analysis_prompt(
    req: GenerateTestcasesRequest,
    leetcode_problem: Optional[dict] = None,
) -> str:
    """Build the problem-analysis prompt.

    Args:
        req: validated request from the user.
        leetcode_problem: real LeetCode problem data fetched via GraphQL
                          (only present when mode=leetcode; None for custom).
    """
    if req.mode == "leetcode" and leetcode_problem:
        lp = leetcode_problem
        examples_block = "\n\n".join(
            f"Example {i + 1}:\n"
            f"  Input : {ex['input']}\n"
            f"  Output: {ex['output']}"
            + (f"\n  Explanation: {ex['explanation']}" if ex.get("explanation") else "")
            for i, ex in enumerate(lp.get("examples") or [])
        ) or "(no examples provided)"

        constraints_block = "\n".join(f"  - {c}" for c in (lp.get("constraints") or [])) \
            or "  (none)"

        starter_py = lp.get("starter_code_python") or "(not provided — infer from description)"
        starter_java = lp.get("starter_code_java") or "(not provided — infer from description)"
        starter_cpp = lp.get("starter_code_cpp") or "(not provided — infer from description)"
        starter_js = lp.get("starter_code_javascript") or "(not provided — infer from description)"

        problem_block = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INPUT — LEETCODE PROBLEM (fetched from official source)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LeetCode #   : {lp.get("number")}
Title        : {lp.get("title")}
Difficulty   : {lp.get("difficulty")}
Official tags: {", ".join(lp.get("tags") or []) or "(none)"}

Description:
{lp.get("description")}

Examples:
{examples_block}

Constraints:
{constraints_block}

Official starter code — Python (Solution class form):
{starter_py}

Official starter code — Java (Solution class form):
{starter_java}

Official starter code — C++ (Solution class form):
{starter_cpp}

Official starter code — JavaScript:
{starter_js}

NOTE: The fields above are the AUTHORITATIVE source of truth for this problem.
- Use VERBATIM (do NOT translate or alter): title, difficulty, tags, the
  function signature, all numbers/identifiers, and the official starter code
  (only normalize whitespace and replace any method body with
  `pass` / empty body / `// TODO`).
- TRANSLATE into natural Vietnamese: the description prose and the constraints
  prose. You MUST keep every code identifier, variable name, function name,
  numeric bound and constraint expression EXACTLY as given (e.g. keep
  `1 <= nums.length <= 10^4`, `nums[i]`, `target` unchanged) — translate only
  the surrounding human-language sentences, not the math/code.
""".strip()
    else:
        hints = []
        if req.difficulty:
            hints.append(f"Difficulty     : {req.difficulty}")
        if req.tags:
            hints.append(f"Tags (hint)    : {', '.join(req.tags)}")
        if req.optimal_time_complexity:
            hints.append(f"Time complexity: {req.optimal_time_complexity}")
        if req.optimal_space_complexity:
            hints.append(f"Space complexity: {req.optimal_space_complexity}")
        hints_str = "\n".join(hints) if hints else "(none provided — infer from description)"

        problem_block = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INPUT — CUSTOM PROBLEM
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Title       : {req.title}
Description :
{req.description}

Additional hints (may be empty):
{hints_str}
""".strip()

    return f"""
You are a SENIOR SOFTWARE ENGINEER with deep expertise in algorithms, data structures,
and coding interview problems. Your task is to analyze a coding problem and produce
complete technical metadata for it.

{problem_block}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OUTPUT REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Produce ALL fields below with strict formatting:

TITLE
  - Exact problem title (capitalize correctly)

DESCRIPTION  (write in natural, fluent VIETNAMESE)
  - ONLY the problem statement prose. Do NOT embed any worked examples —
    no "Example"/"Ví dụ" blocks, no sample Input/Output/Explanation, no
    fenced example snippets — and do NOT embed the constraints list.
    Worked examples are shown to the candidate from the VISIBLE TEST
    CASES, and the limits from the CONSTRAINTS field; duplicating them in
    the description would show them twice in the UI.
  - If LeetCode data was provided above: TRANSLATE the statement into
    Vietnamese (do NOT copy the English) and STRIP its Example /
    Constraints / Follow-up sections — keep only the core statement.
    Keep every code identifier, variable name and number verbatim.
  - If mode=custom: write the statement in Vietnamese from the input,
    likewise without an examples or constraints section.
  - Do NOT translate the TITLE — keep it in its original language.

DIFFICULTY
  - Must be exactly one of: EASY, MEDIUM, HARD (uppercase)

TAGS
  - Maximum 5 tags, lowercase-hyphen format
  - If LeetCode official tags were provided, use them (normalised)
  - Use standard LeetCode tags: array, string, hash-table, tree, graph,
    dynamic-programming, binary-search, two-pointers, sliding-window, stack,
    queue, heap, linked-list, math, bit-manipulation, backtracking, greedy, sorting

OPTIMAL_TIME_COMPLEXITY
  - Big-O notation: O(1), O(log n), O(n), O(n log n), O(n²), O(2^n), etc.
  - Use lowercase n, use ² not ^2

OPTIMAL_SPACE_COMPLEXITY
  - Same Big-O format as above

FUNCTION SIGNATURE
  fn           : function name in camelCase (Java/JS convention)
  params       : list of {{name, type}}
  return_type  : return type string
  order_matters: true if output array/list order is significant
                 (false for problems like twoSum, groupAnagrams, permutations)
  in_place     : true if function mutates the FIRST argument instead of returning
                 a new value (e.g. sortColors, reverseString, rotate). When true,
                 set return_type to "void".

  ALLOWED type strings (these are the ONLY valid values — pick the closest match;
  do NOT invent new ones, do NOT use Python/JS-only types like float/list/object):
    Primitive : int, long, double, boolean
    Boxed     : Integer, Long, Double, Boolean   (auto-normalized to primitive)
    Character : char, Character
    String    : String, string
    1-D array : int[], long[], double[], String[], string[]
    2-D array : int[][], char[][], String[][]
    List      : List<Integer>, List<String>,
                List<List<Integer>>, List<List<String>>
    Tree      : TreeNode      (level-order BFS array representation)
    Linked    : ListNode      (flat int array representation)
    Void only : "void"        (only valid for return_type when in_place=true)

  Notes:
    • Use `double` (NOT `float`) for floating-point.
    • Map Python `list[int]` / `List[int]` → `int[]` (or `List<Integer>` for the
      Java idiom) — pick whichever matches the canonical LeetCode signature.
    • Use `String[][]` for `list[list[str]]`, `int[][]` for `list[list[int]]`.
    • Each char in `char[][]` is a 1-character string.
    • TreeNode input is a JSON array like `[3, 9, 20, null, null, 15, 7]`.
    • ListNode input is a JSON array like `[1, 2, 3, 4, 5]`.

STARTER_CODE  (object with FOUR fields: python, java, cpp, javascript)

  Hard requirements that apply to ALL four languages:
    1. Method body is a single placeholder ONLY (`pass` / `// TODO` / `return ...`
       no real algorithm) — never write the solution.
    2. Method/parameter names MUST match `fn` and `params[].name` exactly.
    3. The judge's stdin/stdout drivers require the EXACT class/function shapes
       below — do NOT change them, do NOT add `public` to the class, do NOT add
       imports unless required by the signature itself, do NOT add docstrings or
       comments beyond the placeholder.
    4. **Data-structure prologue (REQUIRED when ListNode or TreeNode appears in
       any param/return type):** prepend a commented-out class definition above
       the Solution class so candidates can see the shape. Use the LeetCode-style
       block exactly as shown in the templates below — comments only, do NOT
       uncomment them. If neither ListNode nor TreeNode is used, omit the prologue.

  ── python ───────────────────────────────────────────────────────────────────
  Format (judge driver instantiates `Solution()` and calls `getattr(self, fn)`):

      class Solution:
          def {{fn}}(self, {{params}}) -> {{return_hint}}:
              pass

  Prologues (include verbatim ABOVE `class Solution` only when needed):

    ListNode prologue (when any ListNode appears):
      # Definition for singly-linked list.
      # class ListNode:
      #     def __init__(self, val=0, next=None):
      #         self.val = val
      #         self.next = next

    TreeNode prologue (when any TreeNode appears):
      # Definition for a binary tree node.
      # class TreeNode:
      #     def __init__(self, val=0, left=None, right=None):
      #         self.val = val
      #         self.left = left
      #         self.right = right

  - Use Python type hints that map from the schema types:
      int / long → int   |   double → float   |   boolean → bool
      String → str       |   char → str       |   int[] → list[int]
      String[] → list[str]   |   int[][] → list[list[int]]
      char[][] → list[list[str]]   |   List<Integer> → list[int]
      List<List<Integer>> → list[list[int]]   |   List<String> → list[str]
      TreeNode → 'TreeNode' (forward ref — class is provided by driver)
      ListNode → 'ListNode' (forward ref — class is provided by driver)
      void return → -> None
  - If `in_place=true`, the return hint is `-> None`.

  ── java ─────────────────────────────────────────────────────────────────────
  Format (driver creates `new Solution()` and invokes via reflection — class
  must NOT be `public`):

      class Solution {{
          public {{return_type}} {{fn}}({{params}}) {{
              // TODO
          }}
      }}

  Prologues (include verbatim ABOVE `class Solution` only when needed):

    ListNode prologue (when any ListNode appears):
      /**
       * Definition for singly-linked list.
       * public class ListNode {{
       *     int val;
       *     ListNode next;
       *     ListNode() {{}}
       *     ListNode(int val) {{ this.val = val; }}
       *     ListNode(int val, ListNode next) {{ this.val = val; this.next = next; }}
       * }}
       */

    TreeNode prologue (when any TreeNode appears):
      /**
       * Definition for a binary tree node.
       * public class TreeNode {{
       *     int val;
       *     TreeNode left;
       *     TreeNode right;
       *     TreeNode() {{}}
       *     TreeNode(int val) {{ this.val = val; }}
       *     TreeNode(int val, TreeNode left, TreeNode right) {{
       *         this.val = val; this.left = left; this.right = right;
       *     }}
       * }}
       */

  - Java type mapping: int/long/double/boolean stay primitive; String → String;
    char → char; int[]/int[][]/char[][]/String[]/String[][] use Java arrays;
    `List<Integer>`/`List<String>`/`List<List<Integer>>`/`List<List<String>>`
    use `java.util.List` (no import needed if you fully qualify, e.g.
    `java.util.List<Integer>`); TreeNode/ListNode are pre-defined by the driver.
  - For `void` returns return type is `void` and the body still ends with `// TODO`.
  - Provide a sensible default `return ...;` for non-void primitives so the stub
    compiles (e.g. `return 0;`, `return false;`, `return null;`).

  ── cpp ──────────────────────────────────────────────────────────────────────
  Format (LeetCode-compatible Solution class — single TU):

      class Solution {{
      public:
          {{return_type}} {{fn}}({{params}}) {{
              // TODO
          }}
      }};

  Prologues (include verbatim ABOVE `class Solution` only when needed):

    ListNode prologue (when any ListNode appears):
      /**
       * Definition for singly-linked list.
       * struct ListNode {{
       *     int val;
       *     ListNode *next;
       *     ListNode() : val(0), next(nullptr) {{}}
       *     ListNode(int x) : val(x), next(nullptr) {{}}
       *     ListNode(int x, ListNode *next) : val(x), next(next) {{}}
       * }};
       */

    TreeNode prologue (when any TreeNode appears):
      /**
       * Definition for a binary tree node.
       * struct TreeNode {{
       *     int val;
       *     TreeNode *left;
       *     TreeNode *right;
       *     TreeNode() : val(0), left(nullptr), right(nullptr) {{}}
       *     TreeNode(int x) : val(x), left(nullptr), right(nullptr) {{}}
       *     TreeNode(int x, TreeNode *left, TreeNode *right)
       *         : val(x), left(left), right(right) {{}}
       * }};
       */

  - C++ type mapping: int/long long/double/bool/char stay primitive;
    String → string; int[] → vector<int>; int[][] → vector<vector<int>>;
    char[][] → vector<vector<char>>; String[] → vector<string>;
    String[][] → vector<vector<string>>; List<Integer> → vector<int>;
    List<String> → vector<string>; List<List<Integer>> → vector<vector<int>>;
    List<List<String>> → vector<vector<string>>;
    TreeNode → `TreeNode*`; ListNode → `ListNode*`.
  - Use `using namespace std;` style (omit `std::` qualification) to match
    LeetCode's default starter.
  - Provide `return {{}};` / `return 0;` / `return false;` / `return nullptr;` so
    the stub compiles.

  ── javascript ───────────────────────────────────────────────────────────────
  Format (LeetCode-style top-level function expression with JSDoc):

      /**
       * @param {{<jsdoc-type>}} {{paramName}}
       * ...
       * @return {{<jsdoc-type>}}
       */
      var {{fn}} = function({{params}}) {{
          // TODO
      }};

  Prologues (include verbatim ABOVE the `var {{fn}} = ...` only when needed):

    ListNode prologue (when any ListNode appears):
      /**
       * Definition for singly-linked list.
       * function ListNode(val, next) {{
       *     this.val = (val===undefined ? 0 : val)
       *     this.next = (next===undefined ? null : next)
       * }}
       */

    TreeNode prologue (when any TreeNode appears):
      /**
       * Definition for a binary tree node.
       * function TreeNode(val, left, right) {{
       *     this.val = (val===undefined ? 0 : val)
       *     this.left = (left===undefined ? null : left)
       *     this.right = (right===undefined ? null : right)
       * }}
       */

  - JSDoc type mapping: int/long/double → `number`; boolean → `boolean`;
    String/char → `string`; int[]/long[]/double[] → `number[]`;
    String[] → `string[]`; int[][] → `number[][]`; char[][] → `character[][]`;
    String[][] → `string[][]`; List<Integer> → `number[]`;
    List<String> → `string[]`; List<List<Integer>> → `number[][]`;
    List<List<String>> → `string[][]`; TreeNode → `TreeNode`;
    ListNode → `ListNode`; void return → `void`.

  Examples (for fn=twoSum, params=[nums:int[], target:int], return=int[]):

    python:
      class Solution:
          def twoSum(self, nums: list[int], target: int) -> list[int]:
              pass

    java:
      class Solution {{
          public int[] twoSum(int[] nums, int target) {{
              // TODO
              return new int[]{{}};
          }}
      }}

    cpp:
      class Solution {{
      public:
          vector<int> twoSum(vector<int>& nums, int target) {{
              // TODO
              return {{}};
          }}
      }};

    javascript:
      /**
       * @param {{number[]}} nums
       * @param {{number}} target
       * @return {{number[]}}
       */
      var twoSum = function(nums, target) {{
          // TODO
      }};

  Examples WITH data-structure prologue
  (for fn=reverseList, params=[head:ListNode], return=ListNode):

    python:
      # Definition for singly-linked list.
      # class ListNode:
      #     def __init__(self, val=0, next=None):
      #         self.val = val
      #         self.next = next
      class Solution:
          def reverseList(self, head: 'ListNode') -> 'ListNode':
              pass

    java:
      /**
       * Definition for singly-linked list.
       * public class ListNode {{
       *     int val;
       *     ListNode next;
       *     ListNode() {{}}
       *     ListNode(int val) {{ this.val = val; }}
       *     ListNode(int val, ListNode next) {{ this.val = val; this.next = next; }}
       * }}
       */
      class Solution {{
          public ListNode reverseList(ListNode head) {{
              // TODO
              return null;
          }}
      }}

    cpp:
      /**
       * Definition for singly-linked list.
       * struct ListNode {{
       *     int val;
       *     ListNode *next;
       *     ListNode() : val(0), next(nullptr) {{}}
       *     ListNode(int x) : val(x), next(nullptr) {{}}
       *     ListNode(int x, ListNode *next) : val(x), next(next) {{}}
       * }};
       */
      class Solution {{
      public:
          ListNode* reverseList(ListNode* head) {{
              // TODO
              return nullptr;
          }}
      }};

    javascript:
      /**
       * Definition for singly-linked list.
       * function ListNode(val, next) {{
       *     this.val = (val===undefined ? 0 : val)
       *     this.next = (next===undefined ? null : next)
       * }}
       */
      /**
       * @param {{ListNode}} head
       * @return {{ListNode}}
       */
      var reverseList = function(head) {{
          // TODO
      }};

  When LeetCode official starter code was supplied above for a language, use it
  VERBATIM (only normalize whitespace and ensure the body is a placeholder). The
  LeetCode-supplied starter already includes the correct ListNode/TreeNode
  prologue when applicable — keep it as-is.

CONSTRAINTS  (one entry per constraint; prose in VIETNAMESE)
  - List all input constraints: value ranges, array sizes, character sets, etc.
  - Keep every mathematical/expression part EXACTLY (e.g.
    `2 <= nums.length <= 10^4`, `-10^9 <= nums[i] <= 10^9`); only any
    surrounding words are Vietnamese. A bare bound with no prose stays as-is.
  - If LeetCode provided constraints, translate any prose to Vietnamese but
    keep all numbers / expressions / identifiers unchanged.

EDGE_CASE_HINTS  (in VIETNAMESE)
  - List 3–6 specific edge cases relevant to this problem
  - Examples: "mảng rỗng", "một phần tử", "toàn số âm",
    "có giá trị trùng", "không tìm thấy target", "đầu vào ở biên ràng buộc"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Fill ALL fields. Do not leave any field empty or null.
""".strip()
