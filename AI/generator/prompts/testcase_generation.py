from models.generator_inputs import GenerateTestcasesRequest


def build_testcase_generation_prompt(
    req: GenerateTestcasesRequest,
    analysis: dict,
    retry_instruction: str = "",
) -> str:
    num_visible = req.num_visible
    params_str = ", ".join(
        f"{p['name']}: {p['type']}" for p in analysis["params"]
    )
    constraints_str = "\n".join(f"  - {c}" for c in analysis["constraints"]) or "  (none specified)"
    edge_hints_str = "\n".join(f"  - {h}" for h in analysis["edge_case_hints"]) or "  (none specified)"

    retry_block = ""
    if retry_instruction:
        retry_block = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RETRY — FIX THE FOLLOWING ISSUES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Your previous response was rejected. Fix ALL issues listed below:

{retry_instruction}

Regenerate the COMPLETE list of {req.num_testcases} testcases from scratch.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""

    return f"""
You are a SENIOR SOFTWARE ENGINEER specializing in algorithm testing. Your task is to
generate a comprehensive set of test cases for the following coding problem.

{retry_block}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PROBLEM
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Title       : {analysis["title"]}
Difficulty  : {analysis["difficulty"]}
Description :
{analysis["description"]}

Function    : {analysis["fn"]}({params_str}) -> {analysis["return_type"]}
orderMatters: {analysis["order_matters"]}
inPlace     : {analysis["in_place"]}

Optimal Time  : {analysis["optimal_time_complexity"]}
Optimal Space : {analysis["optimal_space_complexity"]}

Constraints:
{constraints_str}

Edge case hints:
{edge_hints_str}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TESTCASE REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Generate EXACTLY {req.num_testcases} test cases with this distribution:

VISIBLE ({num_visible} cases, is_hidden=false):
  ┌─ 1–2 Sample cases
  │   Taken directly from the examples in the problem description.
  │   Use the exact input/output values shown in the problem.
  ├─ 2–3 Edge cases
  │   Cover boundary conditions: empty input, single element, all same values,
  │   negative numbers, zero, maximum constraint values, duplicates, etc.
  └─ Remaining: Normal cases
      Random valid inputs that test correctness under typical conditions.

HIDDEN ({req.num_hidden} cases, is_hidden=true):
  Large or complex inputs designed to distinguish:
  - O(n) solutions from O(n²) brute force
  - Inputs near the maximum constraint bounds
  - Cases that expose incorrect greedy assumptions or missed edge cases

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRICT RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. EXACTLY {req.num_testcases} testcases total — no more, no less.
2. EXACTLY {req.num_hidden} testcases must have is_hidden=true.
3. No two testcases may have identical inputData.
4. expectedOutput must be CORRECT — compute the answer yourself step by step.
5. Each id must be unique: "tc-1", "tc-2", ..., "tc-{req.num_testcases}".
6. Each testcase needs:
   - label  : short descriptor, e.g. "Sample — basic example", "Edge — empty array"
   - note   : a candidate-facing explanation of THIS example, written in
              natural VIETNAMESE (LeetCode "Explanation" style) — explain how
              the given input leads to the expected output (e.g.
              "nums[0] + nums[1] = 2 + 7 = 9 nên trả về [0, 1]."). Keep all
              identifiers / numbers verbatim; only the wording is Vietnamese.
              Required for visible cases; for hidden cases a short reason is
              fine.
7. inputData is a JSON STRING (not an object) — serialize your input dict as a JSON string.
   Keys must EXACTLY equal the function parameter names (no extras, no missing,
   no renames): {list(p['name'] for p in analysis['params'])}
   The validator will REJECT any testcase whose inputData has the wrong key set.
   Example: '{{"s": "abcabcbb"}}' or '{{"nums": [1, 2, 3], "target": 4}}'
8. expectedOutput is a JSON STRING (not an object) — must have exactly one key "result".
   Example: '{{"result": 3}}' or '{{"result": [0, 1]}}' or '{{"result": true}}'
   NEVER leave inputData or expectedOutput as an empty string or empty object.

   ── result-value semantics (DRIVER CONTRACT) ──
   - When inPlace=false (most problems): result is the VALUE the function
     returns, with the same JSON shape as return_type. Example for
     twoSum (return=int[]): '{{"result": [0, 1]}}'.
   - When inPlace=true (pure-mutation problems, e.g. sortColors,
     reverseString, rotate): result is the FIRST parameter AFTER the
     in-place mutation, with the same JSON shape as params[0].type. The
     driver ignores the function's return value in this mode. Example for
     sortColors(nums=[2,0,2,1,1,0]): '{{"result": [0, 0, 1, 1, 2, 2]}}'.
   Never put the wrong shape — a scalar where an array is expected (or
   vice-versa) will WA on every case at submit time.

9. ANSWER MUST BE UNIQUE PER INPUT (critical — the judge compares the
   candidate's output to your single `result` with an EXACT match; there is
   no special checker). Many problems accept MORE THAN ONE valid output for
   the same input — e.g. Two Sum (several index pairs may hit the target),
   "return ANY peak / any valid subset / any reordering", problems that say
   "if multiple answers exist, return any". For every such problem you MUST
   CONSTRUCT each input so that EXACTLY ONE valid answer exists, then put that
   answer in `result`. Do not rely on the candidate happening to pick the same
   one you did — they won't.
     • Two Sum: ensure NO other pair besides your intended one sums to target
       (check ALL pairs, not just yours). If a second pair also works, change
       a number so it no longer does.
     • "any valid X": pick inputs with a single feasible X (e.g. a strictly
       single peak, a target reachable one way only).
     • Order-insensitive collections: set orderMatters semantics via the
       contract above; uniqueness here is about the SET of elements, not order.
   If you cannot make the answer unique for a candidate input, discard that
   input and choose a different one. An ambiguous input is a WRONG testcase
   even when your `result` is "a" correct answer.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Think step by step:
1. Read the problem and constraints carefully.
2. Decide whether the problem can have MORE THAN ONE valid output for a given
   input (see rule 9). If so, every input you craft must pin the answer down
   to exactly one — verify by scanning for alternative valid answers.
3. Identify the sample examples from the description → use as Sample cases.
4. Think of all boundary conditions → use as Edge cases.
5. Generate diverse normal inputs → Normal cases.
6. Design large/tricky inputs → Hidden cases.
7. For EACH testcase, compute the expectedOutput manually before writing it,
   AND confirm no OTHER output would also be accepted for that input.
8. Verify: count=={req.num_testcases}, hidden=={req.num_hidden}, no duplicate inputs.
""".strip()
