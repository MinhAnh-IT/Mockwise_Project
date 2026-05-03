"""Single-shot follow-up question generator.

Synchronous: one Gemini call via Instructor → structured FollowUpResponse.
No graph, no retries, no Kafka — interview-service blocks on this call
because the user is waiting for the next question.
"""
from __future__ import annotations

import time

import config
from llm import call_structured
from evaluator.prompts.follow_up import build_follow_up_prompt
from models.follow_up import FollowUpRequest, FollowUpResponse


def generate_follow_up(req: FollowUpRequest) -> FollowUpResponse:
    started_ms = int(time.time() * 1000)
    prompt = build_follow_up_prompt(req)
    result: FollowUpResponse = call_structured(prompt, FollowUpResponse)

    # Stamp meta — the model is told to leave model_meta empty and source=AI_GENERATED.
    duration_ms = max(0, int(time.time() * 1000) - started_ms)
    result.source = "AI_GENERATED"
    result.model_meta = {
        "model": config.MODEL_NAME,
        "duration_ms": duration_ms,
        "evaluator_version": config.EVALUATOR_VERSION,
    }
    return result
