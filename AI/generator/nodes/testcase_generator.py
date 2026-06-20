import json
from typing import List, Any, Dict

from pydantic import BaseModel, field_validator

import config
from llm import call_structured
from generator.prompts.testcase_generation import build_testcase_generation_prompt
from generator.state import GeneratorState
from models.generator_inputs import GenerateTestcasesRequest


# ── Internal Pydantic model used with Instructor ──────────────────────────────
# inputData and expectedOutput are JSON strings to avoid Dict[str, Any] schema
# issues with Gemini structured output (which returns {} for open-ended dicts).

class _TestCase(BaseModel):
    id: str
    label: str
    inputData: str   # JSON string, e.g. '{"s": "abcabcbb"}'
    expectedOutput: str  # JSON string, e.g. '{"result": 3}'
    is_hidden: bool
    note: str

    @field_validator("inputData", "expectedOutput", mode="after")
    @classmethod
    def must_be_non_empty_json_object(cls, v: str) -> str:
        try:
            parsed = json.loads(v)
        except (json.JSONDecodeError, TypeError):
            raise ValueError(f"Must be a valid JSON string, got: {v!r}")
        if not isinstance(parsed, dict) or not parsed:
            raise ValueError(f"Must be a non-empty JSON object, got: {v!r}")
        return v

    def input_dict(self) -> Dict[str, Any]:
        return json.loads(self.inputData)

    def output_dict(self) -> Dict[str, Any]:
        return json.loads(self.expectedOutput)


class _TestcaseList(BaseModel):
    testcases: List[_TestCase]


# ── Node ──────────────────────────────────────────────────────────────────────

def testcase_generator_node(state: GeneratorState) -> dict:
    """
    Call Gemini via Instructor to generate testcases based on problem_analysis.
    Passes retry_instruction if this is a retry attempt.
    """
    req: GenerateTestcasesRequest = state["validated_input"]
    analysis: dict = state["problem_analysis"]
    retry_count: int = state.get("retry_count", 0)
    previous_error: str = state.get("generation_error", "") or ""

    retry_instruction = previous_error if retry_count > 0 else ""

    try:
        prompt = build_testcase_generation_prompt(req, analysis, retry_instruction)

        # 20 testcases × ~400 tokens each = ~8k; scale up with count but CLAMP to
        # the model's hard output limit (gemini-3.1-pro-preview = 65536). A 100-case
        # batch needs ~60k tokens — fits — so the output won't truncate; the cap
        # (MAX_TESTCASES=100) keeps requests inside this budget.
        max_tokens = min(65536, max(8192, req.num_testcases * 600))
        result: _TestcaseList = call_structured(
            prompt,
            _TestcaseList,
            max_tokens=max_tokens,
            model=config.GENERATOR_MODEL_NAME,
            thinking_level=config.GENERATOR_THINKING_LEVEL,
        )

        # Convert string fields back to dicts for downstream validators
        raw = [
            {
                "id": tc.id,
                "label": tc.label,
                "inputData": tc.input_dict(),
                "expectedOutput": tc.output_dict(),
                "is_hidden": tc.is_hidden,
                "note": tc.note,
            }
            for tc in result.testcases
        ]

        return {
            "raw_testcases": raw,
            "generation_error": None,
        }

    except Exception as exc:
        return {
            "raw_testcases": None,
            "generation_error": f"TestcaseGenerator error: {exc}",
        }
