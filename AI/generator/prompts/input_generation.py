def build_input_generation_prompt(analysis: dict, num_cases: int) -> str:
    """Prompt the model to author a Python ``gen_inputs(num_cases, seed)`` function
    that PROGRAMMATICALLY produces large/random hidden-testcase inputs.

    The model cannot hand-type a 10^4-element array, but it CAN write a small
    generator that emits one — so we ask for the generator, run it ourselves, and
    let expected_verifier compute the outputs via the reference solution.
    """
    params = analysis["params"]
    params_str = ", ".join(f"{p['name']}: {p['type']}" for p in params)
    param_names = [p["name"] for p in params]
    constraints_str = "\n".join(f"  - {c}" for c in analysis.get("constraints") or []) \
        or "  (none specified — pick reasonable interview-scale bounds)"

    return f"""
You are a SENIOR TEST ENGINEER. Write a single Python function that generates
LARGE, RANDOM, CONSTRAINT-VALID inputs for the coding problem below. These
inputs become HIDDEN testcases that stress an O(n) solution against an O(n²)
brute force, so they must reach near the maximum constraint bounds.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PROBLEM
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Title    : {analysis["title"]}
Function : {analysis["fn"]}({params_str}) -> {analysis["return_type"]}

Constraints:
{constraints_str}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WHAT TO WRITE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Write EXACTLY one function with this signature:

    def gen_inputs(num_cases, seed):
        ...
        return cases

Hard requirements:
1. Return a Python list of EXACTLY `num_cases` dicts. Each dict's keys are
   EXACTLY the parameter names — no more, no less: {param_names}
2. Every value MUST be JSON-serialisable and use the SAME representation the
   judge driver expects for that parameter's type:
     - int / long          → a Python int
     - double              → a Python float
     - boolean             → a Python bool
     - String / char       → a Python str (char = 1-character str)
     - int[] / long[]      → list[int]      ;  double[] → list[float]
     - String[]            → list[str]
     - int[][]             → list[list[int]] ;  char[][] → list[list[str]]
     - List<Integer>       → list[int]      ;  List<String> → list[str]
     - List<List<Integer>> → list[list[int]]
     - TreeNode            → flat LEVEL-ORDER array with `None` for missing
                             children, e.g. [3, 9, 20, None, None, 15, 7]
     - ListNode            → flat list[int], e.g. [1, 2, 3, 4, 5]
3. EVERY generated value MUST satisfy ALL the constraints above (lengths, value
   ranges, character sets, sortedness/uniqueness if required, valid tree/list
   shapes). An out-of-constraint input is a BUG.
4. Be DETERMINISTIC given `seed`: create `rng = random.Random(seed)` and draw
   ALL randomness from `rng`. Never touch the global `random` state.
5. Produce a SPREAD of sizes that LEANS LARGE: most cases should sit near the
   maximum length/value bounds (that is the whole point — to expose slow
   solutions), with a few mid-size ones for variety. Make the cases distinct.
6. Self-contained: only the Python standard library (`random`, `string`, etc.).
   No I/O, no printing, no network, no third-party imports. Do NOT define
   `Solution`, `ListNode`, or `TreeNode` — only `gen_inputs`.

Return ONLY the function source in the `source` field (no markdown fences, no
prose). It will be exec'd as-is.
""".strip()
