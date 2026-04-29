"""Shared Gemini client helpers — structured chat + embeddings.

Used by both the evaluation graph (call_structured) and the question
selector (embed_text / embed_batch). Centralising here keeps model and
thinking config aligned across both feature areas.
"""
from typing import Any, List

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


def embed_text(text: str) -> List[float]:
    """Embed a single text. Defaults to text-embedding-004 (768 dim)."""
    client = genai.Client(api_key=config.GOOGLE_API_KEY)
    result = client.models.embed_content(
        model=config.EMBEDDING_MODEL,
        contents=text,
    )
    return list(result.embeddings[0].values)


def embed_batch(texts: List[str]) -> List[List[float]]:
    """Embed a batch of texts in one API call. Order preserved."""
    if not texts:
        return []
    client = genai.Client(api_key=config.GOOGLE_API_KEY)
    result = client.models.embed_content(
        model=config.EMBEDDING_MODEL,
        contents=texts,
    )
    return [list(emb.values) for emb in result.embeddings]
