"""Parse the raw `session-evaluation-requested` payload into a typed
`SessionEvaluationPayload`. On schema failure we short-circuit with a
final error dict so the consumer can publish `session-evaluation-failed`
and the orchestrator can record the issue without retrying."""
from __future__ import annotations

import logging
import time

from pydantic import ValidationError

from models.session_review import SessionEvaluationPayload
from overall_reviewer.state import OverallReviewState

logger = logging.getLogger(__name__)


def input_validator_node(state: OverallReviewState) -> dict:
    raw = state.get("raw_input") or {}
    try:
        validated = SessionEvaluationPayload.model_validate(raw)
    except ValidationError as exc:
        session_id = raw.get("sessionId") or raw.get("session_id") or "unknown"
        logger.warning("Overall reviewer: invalid input for session %s — %s", session_id, exc)
        return {
            "validated_input": None,
            "evaluation_start_ms": int(time.time() * 1000),
            "final_output": {
                "session_id": session_id,
                "error": "invalid_input",
                "detail": str(exc),
            },
        }

    return {
        "validated_input": validated,
        "evaluation_start_ms": int(time.time() * 1000),
        "retry_count": 0,
        "needs_retry": False,
        "evaluation_error": None,
    }
