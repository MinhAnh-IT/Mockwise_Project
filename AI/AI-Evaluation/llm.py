"""Shared Gemini client helper.

Keeps all Gemini calls on the same model/mode/thinking config so the whole
service scales the same way when we swap models.
"""
from typing import Any

import instructor
from google import genai
from google.genai import types

import config


def _thinking_config() -> types.ThinkingConfig:
    level = config.THINKING_LEVEL
    if config.MODEL_NAME.startswith("gemini-3"):
        return types.ThinkingConfig(thinking_level=level)
    # Gemini 2.x uses thinking_budget (token allowance) instead of thinking_level
    budget_map = {"minimal": 0, "low": 1024, "medium": 4096, "high": 8192}
    return types.ThinkingConfig(thinking_budget=budget_map.get(level, 1024))


def call_structured(prompt: str, response_model: type, max_tokens: int = 8192) -> Any:
    """Call Gemini with structured-output mode and return the parsed pydantic model."""
    client = instructor.from_genai(
        genai.Client(api_key=config.GOOGLE_API_KEY),
        mode=instructor.Mode.GENAI_STRUCTURED_OUTPUTS,
    )
    return client.chat.completions.create(
        model=config.MODEL_NAME,
        messages=[{"role": "user", "content": prompt}],
        response_model=response_model,
        generation_config={"temperature": 0.4, "max_tokens": max_tokens},
        config={"thinking_config": _thinking_config()},
    )
