from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field, ConfigDict
from pydantic.alias_generators import to_camel


_CAMEL_CONFIG = ConfigDict(
    alias_generator=to_camel,
    populate_by_name=True,
)


class ParamMeta(BaseModel):
    name: str
    type: str


class FunctionMeta(BaseModel):
    """
    Serializes to judge-service format:
    { "fn": ..., "params": [...], "return": ..., "orderMatters": ..., "inPlace": ... }
    Always serialize with `.model_dump(by_alias=True)` when sending to judge-service.
    """
    model_config = ConfigDict(populate_by_name=True)

    fn: str
    params: List[ParamMeta]
    # field-level alias overrides any model-level alias_generator
    return_type: str = Field(alias="return")
    orderMatters: bool
    inPlace: bool


class StarterCode(BaseModel):
    """Starter code stubs for the four front-end languages."""
    python: str
    java: str
    cpp: str
    javascript: str


class GeneratedTestCase(BaseModel):
    model_config = _CAMEL_CONFIG

    id: str
    label: str                    # e.g. "Edge — empty array"
    inputData: Dict[str, Any]
    expectedOutput: Dict[str, Any]
    is_hidden: bool               # JSON: isHidden
    note: str                     # why this testcase matters


class GeneratedMeta(BaseModel):
    model_config = _CAMEL_CONFIG

    mode: str
    leetcode_number: Optional[int] = None      # JSON: leetcodeNumber
    generated_at: str                          # JSON: generatedAt
    model_version: str                         # JSON: modelVersion
    generation_duration_ms: int                # JSON: generationDurationMs
    total_testcases: int                       # JSON: totalTestcases
    visible_testcases: int                     # JSON: visibleTestcases
    hidden_testcases: int                      # JSON: hiddenTestcases


class GenerateTestcasesResponse(BaseModel):
    model_config = _CAMEL_CONFIG

    title: str
    description: str
    difficulty: str                            # EASY | MEDIUM | HARD
    tags: List[str]
    time_limit_minutes: int                    # JSON: timeLimitMinutes
    optimal_time_complexity: str               # JSON: optimalTimeComplexity
    optimal_space_complexity: str              # JSON: optimalSpaceComplexity
    function_meta: FunctionMeta                # JSON: functionMeta
    starter_code: StarterCode                  # JSON: starterCode (python|java|cpp|javascript)
    testcases: List[GeneratedTestCase]
    meta: GeneratedMeta
    warning: Optional[str] = None
