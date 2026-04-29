import logging
import time
from datetime import datetime, timezone
from typing import Any

import config

logger = logging.getLogger(__name__)
from evaluator.state import AgentState
from models.common import Grade, HireSignal, MetaBlock, SummaryBlock
from models.outputs import LiveCodingOutput, BehavioralOutput, ConceptualOutput


# ─── Grade / hire_signal auto-correction table ────────────────────────────────

def _correct_grade_and_signal(score: int) -> tuple[Grade, HireSignal]:
    """Deterministically derive grade and hire_signal from overall_score."""
    if score >= 90:
        return Grade.A, HireSignal.strong_yes
    elif score >= 75:
        return Grade.B, HireSignal.yes
    elif score >= 60:
        return Grade.C, HireSignal.weak_yes
    elif score >= 45:
        return Grade.D, HireSignal.no
    else:
        return Grade.F, HireSignal.strong_no


# ─── Weighted score formulas per interview type ───────────────────────────────

def _compute_expected_score_live_coding(output: LiveCodingOutput) -> float:
    s = output.scores
    return (
        s.time_complexity.score  * 0.30 +
        s.space_complexity.score * 0.15 +
        s.code_quality.score     * 0.35 +
        s.problem_solving.score  * 0.20
    )


def _compute_expected_score_behavioral(output: BehavioralOutput) -> float:
    s = output.scores
    return (
        s.star_structure.score  * 0.25 +
        s.relevance.score       * 0.20 +
        s.specificity.score     * 0.25 +
        s.impact_result.score   * 0.20 +
        s.self_awareness.score  * 0.10
    )


def _compute_expected_score_conceptual(output: ConceptualOutput) -> float:
    s = output.scores
    return (
        s.accuracy.score              * 0.35 +
        s.depth.score                 * 0.30 +
        s.practical_application.score * 0.20 +
        s.clarity.score               * 0.15
    )


# ─── Inject MetaBlock ─────────────────────────────────────────────────────────

def _inject_meta(output: Any, evaluation_start_ms: int) -> Any:
    """Overwrite the meta block with real system values."""
    now_ms = int(time.time() * 1000)
    duration_ms = max(0, now_ms - evaluation_start_ms)
    evaluated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    output.meta = MetaBlock(
        evaluated_at=evaluated_at,
        model_version=config.MODEL_VERSION,
        evaluation_duration_ms=duration_ms,
    )
    return output


# ─── Main validator node ──────────────────────────────────────────────────────

def output_validator_node(state: AgentState) -> dict:
    """
    Validate the raw_output produced by an evaluator node:

    1. Verify raw_output is not None.
    2. Auto-correct grade and hire_signal based on overall_score (deterministic).
    3. Check consistency between overall_score and weighted sub-scores (±5 tolerance).
       If inconsistent, request a retry.
    4. Inject real MetaBlock values.
    5. On success: serialise to dict and set final_output.
    6. On retry needed: increment retry_count and set evaluation_error.
    7. On retry exhausted: set final_output to an error dict.
    """
    raw_output: Any = state.get("raw_output")
    retry_count: int = state.get("retry_count", 0)
    interview_type: str = state.get("interview_type", "")
    evaluation_start_ms: int = state.get("evaluation_start_ms", int(time.time() * 1000))
    session_id: str = state["raw_input"].get("session_id", "unknown")

    # ── 1. Check raw_output is present ───────────────────────────────────────
    if raw_output is None:
        error_detail = state.get("evaluation_error") or "Evaluator produced no output."
        if retry_count < config.MAX_RETRIES:
            return {
                "needs_retry": True,
                "retry_count": retry_count + 1,
                "evaluation_error": (
                    f"Evaluation failed with no output. Please produce a complete, valid response. "
                    f"Error context: {error_detail}"
                ),
            }
        return {
            "needs_retry": False,
            "final_output": {
                "session_id": session_id,
                "error": "evaluation_failed",
                "detail": error_detail,
                "retry_attempts": retry_count,
            },
        }

    # ── 2. Auto-correct grade and hire_signal ────────────────────────────────
    overall_score: int = raw_output.overall_score
    corrected_grade, corrected_signal = _correct_grade_and_signal(overall_score)
    raw_output.summary.grade = corrected_grade
    raw_output.summary.hire_signal = corrected_signal

    # ── 3. Check weighted score consistency ──────────────────────────────────
    try:
        if interview_type == "live_coding":
            expected = _compute_expected_score_live_coding(raw_output)
        elif interview_type == "behavioral":
            expected = _compute_expected_score_behavioral(raw_output)
        elif interview_type == "core_conceptual":
            expected = _compute_expected_score_conceptual(raw_output)
        else:
            expected = float(overall_score)  # unknown type, skip check

        deviation = abs(overall_score - expected)
        tolerance = 5.0

        if deviation > tolerance:
            error_msg = (
                f"overall_score ({overall_score}) is inconsistent with the weighted sum of "
                f"sub-scores ({expected:.1f}). The deviation is {deviation:.1f} points, "
                f"which exceeds the allowed tolerance of {tolerance} points. "
                f"Please recalculate overall_score as: "
                f"round(weighted_sum_of_dimension_scores) = {round(expected)}."
            )
            if retry_count < config.MAX_RETRIES:
                return {
                    "needs_retry": True,
                    "retry_count": retry_count + 1,
                    "evaluation_error": error_msg,
                }
            # Retry exhausted — auto-fix the score instead of failing entirely
            raw_output.overall_score = round(expected)
            corrected_grade, corrected_signal = _correct_grade_and_signal(round(expected))
            raw_output.summary.grade = corrected_grade
            raw_output.summary.hire_signal = corrected_signal

    except Exception as exc:
        # Non-fatal: log but don't block output
        logger.warning("Score consistency check failed: %s", exc, exc_info=True)

    # ── 4. Inject real MetaBlock ──────────────────────────────────────────────
    raw_output = _inject_meta(raw_output, evaluation_start_ms)

    # ── 5. Serialise and return ───────────────────────────────────────────────
    try:
        final_dict = raw_output.model_dump()
    except Exception as exc:
        final_dict = {"session_id": session_id, "error": "serialization_failed", "detail": str(exc)}

    return {
        "final_output": final_dict,
        "needs_retry": False,
        "raw_output": raw_output,
    }
