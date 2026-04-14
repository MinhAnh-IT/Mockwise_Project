from models.generator_inputs import GenerateTestcasesRequest


def build_testcase_generation_prompt(
    req: GenerateTestcasesRequest,
    analysis: dict,
    retry_instruction: str = "",
) -> str:
    num_visible = req.num_testcases - req.num_hidden
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
   - note   : one sentence explaining why this testcase is important
7. inputData is a JSON STRING (not an object) — serialize your input dict as a JSON string.
   Keys must match the function parameter names exactly: {list(p['name'] for p in analysis['params'])}
   Example: '{{"s": "abcabcbb"}}' or '{{"nums": [1, 2, 3], "target": 4}}'
8. expectedOutput is a JSON STRING (not an object) — must have exactly one key "result".
   Example: '{{"result": 3}}' or '{{"result": [0, 1]}}' or '{{"result": true}}'
   NEVER leave inputData or expectedOutput as an empty string or empty object.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Think step by step:
1. Read the problem and constraints carefully.
2. Identify the sample examples from the description → use as Sample cases.
3. Think of all boundary conditions → use as Edge cases.
4. Generate diverse normal inputs → Normal cases.
5. Design large/tricky inputs → Hidden cases.
6. For EACH testcase, compute the expectedOutput manually before writing it.
7. Verify: count=={req.num_testcases}, hidden=={req.num_hidden}, no duplicate inputs.
""".strip()
