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
from models.follow_up import FollowUpRequest, FollowUpLLMOutput, FollowUpResponse


def generate_follow_up(req: FollowUpRequest) -> FollowUpResponse:
    started_ms = int(time.time() * 1000)
    prompt = build_follow_up_prompt(req)
    # Ask the model for content fields only — server-stamped meta (source,
    # model_meta) is added below and kept out of the Gemini schema.
    # max_tokens is generous: thinking tokens share this budget, and a starved
    # budget is the most likely cause of an empty/truncated structured output.
    llm: FollowUpLLMOutput = call_structured(prompt, FollowUpLLMOutput, max_tokens=16384)

    duration_ms = max(0, int(time.time() * 1000) - started_ms)
    return FollowUpResponse(
        question_text=llm.question_text,
        expected_points=llm.expected_points,
        rationale=llm.rationale,
        source="AI_GENERATED",
        model_meta={
            "model": config.MODEL_NAME,
            "duration_ms": duration_ms,
            "evaluator_version": config.EVALUATOR_VERSION,
        },
    )
