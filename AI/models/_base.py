"""Shared Pydantic base for every wire-facing model in the AI service.

Pydantic field names stay {@code snake_case} (Python convention); the
JSON wire format is {@code camelCase} via {@code alias_generator=to_camel}.
{@code populate_by_name=True} means models also parse incoming JSON whose
keys are still snake_case — useful as a safety net when an upstream
producer hasn't migrated yet.

Why camelCase: the rest of the Mockwise stack is Java/Spring/MapStruct,
where camelCase is canonical. The AI service was the lone snake_case
producer; aligning it removes the per-DTO {@code @JsonNaming} workaround
on the orchestrator side.
"""
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Drop-in replacement for {@code BaseModel} that emits camelCase JSON
    and accepts both camelCase + snake_case for parsing.
    """
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        # Lets Instructor's schema generation pick up the alias names so
        # Gemini sees camelCase field names in the structured-output schema.
        serialize_by_alias=True,
    )
