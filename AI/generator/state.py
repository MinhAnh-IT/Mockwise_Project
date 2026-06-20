from typing import TypedDict, Optional

from models.generator_inputs import GenerateTestcasesRequest


class GeneratorState(TypedDict):
    raw_input: dict
    validated_input: Optional[GenerateTestcasesRequest]
    leetcode_problem: Optional[dict]   # fetched LeetCode data (mode=leetcode only)
    problem_analysis: Optional[dict]   # structured output of problem_analyzer node
    raw_testcases: Optional[list]      # structured output of testcase_generator node
    verifier_warning: Optional[str]    # non-fatal notes from expected_verifier node
    programmatic_warning: Optional[str]  # non-fatal notes from input_generator node
    needs_reference_retry: bool        # expected_verifier → regenerate solutions
    reference_retry_count: int         # repair-loop counter (capped REFERENCE_MAX_RETRIES)
    reference_feedback: Optional[str]  # why the last reference/brute pair was rejected
    verifier_unverified_ids: Optional[list]  # ids of cases left UNVERIFIED (admin must review)
    final_output: Optional[dict]
    retry_count: int
    needs_retry: bool
    generation_error: Optional[str]
    generation_start_ms: Optional[int]
