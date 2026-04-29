from typing import TypedDict, Optional

from models.generator_inputs import GenerateTestcasesRequest


class GeneratorState(TypedDict):
    raw_input: dict
    validated_input: Optional[GenerateTestcasesRequest]
    leetcode_problem: Optional[dict]   # fetched LeetCode data (mode=leetcode only)
    problem_analysis: Optional[dict]   # structured output of problem_analyzer node
    raw_testcases: Optional[list]      # structured output of testcase_generator node
    final_output: Optional[dict]
    retry_count: int
    needs_retry: bool
    generation_error: Optional[str]
    generation_start_ms: Optional[int]
