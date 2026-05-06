"""Single Gemini call producing the qualitative narrative for the session."""
from __future__ import annotations

import logging

from llm import call_structured
from models.session_review import OverallReviewOutput, SessionEvaluationPayload
from overall_reviewer.prompts.overall_review import build_overall_review_prompt
from overall_reviewer.state import OverallReviewState

logger = logging.getLogger(__name__)


def overall_evaluator_node(state: OverallReviewState) -> dict:
    validated: SessionEvaluationPayload = state["validated_input"]
    retry_count: int = state.get("retry_count", 0)
    previous_error: str = state.get("evaluation_error") or ""

    retry_instruction = previous_error if retry_count > 0 else ""

    try:
        prompt = build_overall_review_prompt(validated, retry_instruction=retry_instruction)
        result: OverallReviewOutput = call_structured(prompt, OverallReviewOutput)
        return {
            "raw_output": result,
            "last_prompt": prompt,
            "evaluation_error": None,
        }
    except Exception as exc:
        logger.exception("OverallEvaluator failed for session %s", validated.session_id)
        return {
            "raw_output": None,
            "evaluation_error": f"OverallEvaluator error: {exc}",
        }
