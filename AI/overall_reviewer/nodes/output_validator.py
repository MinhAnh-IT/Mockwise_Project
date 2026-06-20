"""Validate the LLM output, recompute deterministic fields, finalise.

Recompute rules:
- `overall_score` is recomputed from per-answer scores weighted by the
  topic's `importance` (HIGH:1.5, MED:1.0, LOW:0.5) AND the question's
  `difficulty` (EASY:0.8, MEDIUM:1.0, HARD:1.3) so harder questions count
  more. Answers with status FAILED are scored 0. The LLM number is
  overwritten — narrative quality is its job, scoring is ours.
- `grade` and `hire_signal` derive from `overall_score` (0–10 bands):
    A ≥ 9.0      strong_yes
    B ≥ 7.5      yes
    C ≥ 6.0      weak_yes
    D ≥ 4.5      no
    else         strong_no
"""
from __future__ import annotations

import logging
import time
from typing import Any

import config
from models.common import Grade, HireSignal
from models.session_review import OverallReviewOutput, SessionEvaluationPayload
from overall_reviewer.state import OverallReviewState

logger = logging.getLogger(__name__)


_IMPORTANCE_WEIGHT = {"HIGH": 1.5, "MED": 1.0, "LOW": 0.5}
_DIFFICULTY_WEIGHT = {"EASY": 0.8, "MEDIUM": 1.0, "MED": 1.0, "HARD": 1.3}


def _difficulty_weight(difficulty: str | None) -> float:
    if not difficulty:
        return 1.0
    return _DIFFICULTY_WEIGHT.get(difficulty.upper(), 1.0)


def _grade_band(score: float) -> tuple[Grade, HireSignal]:
    if score >= 9.0:
        return Grade.A, HireSignal.strong_yes
    if score >= 7.5:
        return Grade.B, HireSignal.yes
    if score >= 6.0:
        return Grade.C, HireSignal.weak_yes
    if score >= 4.5:
        return Grade.D, HireSignal.no
    return Grade.F, HireSignal.strong_no


def _topic_weight(payload: SessionEvaluationPayload, topic_kind: str | None, topic_value: str | None) -> float:
    if not topic_kind or not topic_value:
        return _IMPORTANCE_WEIGHT["MED"]
    for t in payload.blueprint.topics or []:
        if t.get("kind") == topic_kind and t.get("topicValue") == topic_value:
            imp = (t.get("importance") or "MED").upper()
            return _IMPORTANCE_WEIGHT.get(imp, 1.0)
    return _IMPORTANCE_WEIGHT["MED"]


def _compute_overall_score(payload: SessionEvaluationPayload) -> float:
    """Weighted average of per-answer scores, capped at the 0..10 scale.
    Returns 0.0 if there's nothing to score (every answer FAILED with no score)."""
    weighted_sum = 0.0
    weight_total = 0.0
    for ans in payload.answers:
        score = ans.per_answer_score
        if ans.answer_status == "FAILED" or score is None:
            score = 0.0
        weight = _topic_weight(payload, ans.topic_kind, ans.topic_value) \
            * _difficulty_weight(ans.difficulty)
        weighted_sum += float(score) * weight
        weight_total += weight
    if weight_total == 0:
        return 0.0
    return max(0.0, min(10.0, weighted_sum / weight_total))


def output_validator_node(state: OverallReviewState) -> dict:
    raw_output: Any = state.get("raw_output")
    retry_count: int = state.get("retry_count", 0)
    payload: SessionEvaluationPayload = state["validated_input"]

    if raw_output is None:
        error_detail = state.get("evaluation_error") or "OverallReviewer produced no output."
        if retry_count < config.MAX_RETRIES:
            return {
                "needs_retry": True,
                "retry_count": retry_count + 1,
                "evaluation_error": (
                    "Previous attempt produced no output. Produce a complete, valid response. "
                    f"Context: {error_detail}"
                ),
            }
        return {
            "needs_retry": False,
            "final_output": {
                "session_id": payload.session_id,
                "error": "overall_review_failed",
                "detail": error_detail,
                "retry_attempts": retry_count,
            },
        }

    # Recompute score deterministically from per-answer data.
    computed = _compute_overall_score(payload)
    grade, hire_signal = _grade_band(computed)
    if isinstance(raw_output, OverallReviewOutput):
        raw_output.overall_score = round(computed, 2)
        raw_output.grade = grade
        raw_output.hire_signal = hire_signal
        # Keep session_id authoritative — the LLM might emit a stale one.
        raw_output.session_id = payload.session_id

    try:
        final_dict = raw_output.model_dump(by_alias=True)
    except Exception as exc:
        logger.warning("Failed to serialize OverallReviewOutput: %s", exc)
        final_dict = {
            "session_id": payload.session_id,
            "error": "serialization_failed",
            "detail": str(exc),
        }

    return {
        "final_output": final_dict,
        "needs_retry": False,
        "raw_output": raw_output,
    }
