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

        starter = lp.get("starter_code_python") or "(not provided — infer from description)"

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

Official starter code (Python, Solution class form):
{starter}

NOTE: The fields above are the AUTHORITATIVE source of truth for this problem.
You MUST use them verbatim for: title, description, difficulty, tags, constraints.
Do NOT alter, paraphrase, or "improve" the description.
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

DESCRIPTION
  - Full problem statement including constraints and examples
  - If LeetCode data was provided above: reproduce it faithfully (do NOT rewrite)
  - If mode=custom: use the provided description as-is

DIFFICULTY
  - Must be exactly one of: EASY, MEDIUM, HARD (uppercase)

TAGS
  - Maximum 5 tags, lowercase-hyphen format
  - If LeetCode official tags were provided, use them (normalised)
  - Use standard LeetCode tags: array, string, hash-table, tree, graph,
    dynamic-programming, binary-search, two-pointers, sliding-window, stack,
    queue, heap, linked-list, math, bit-manipulation, backtracking, greedy, sorting

TIME_LIMIT_MINUTES
  - Estimate based on difficulty: EASY=20, MEDIUM=30, HARD=45

OPTIMAL_TIME_COMPLEXITY
  - Big-O notation: O(1), O(log n), O(n), O(n log n), O(n²), O(2^n), etc.
  - Use lowercase n, use ² not ^2

OPTIMAL_SPACE_COMPLEXITY
  - Same Big-O format as above

FUNCTION SIGNATURE
  fn           : function name in camelCase (Java/JS convention)
  params       : list of {{name, type}} — use type strings: int, int[], int[][],
                 string, string[], boolean, float, ListNode, TreeNode
  return_type  : return type string (same conventions as params type)
  order_matters: true if output array/list order is significant
  in_place     : true if function modifies input instead of returning new value

STARTER_CODE
  - Python only
  - Function signature + type hints + pass, nothing else
  - No imports, no docstrings, no comments
  - If LeetCode provided a Solution-class starter, convert it to a plain function.
    Example: `class Solution: def twoSum(self, nums, target):`
          →  `def twoSum(nums: list[int], target: int) -> list[int]: pass`
  - Examples:
      def twoSum(nums: list[int], target: int) -> list[int]:
          pass
      def sortColors(nums: list[int]) -> None:
          pass
      def merge(intervals: list[list[int]]) -> list[list[int]]:
          pass

CONSTRAINTS
  - List all input constraints (e.g. "2 <= nums.length <= 10^4")
  - Include value ranges, array sizes, character sets, etc.
  - If LeetCode provided constraints, use them verbatim.

EDGE_CASE_HINTS
  - List 3–6 specific edge cases relevant to this problem
  - Examples: "empty array", "single element", "all negative numbers",
    "duplicate values", "target not found", "maximum constraint input"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Fill ALL fields. Do not leave any field empty or null.
""".strip()
