from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field, ConfigDict


class ParamMeta(BaseModel):
    name: str
    type: str


class FunctionMeta(BaseModel):
    """
    Serializes to question-bank-service format:
    { "fn": ..., "params": [...], "return": ..., "orderMatters": ..., "inPlace": ... }
    Use .model_dump(by_alias=True) when sending to question-bank-service.
    """
    model_config = ConfigDict(populate_by_name=True)

    fn: str
    params: List[ParamMeta]
    return_type: str = Field(serialization_alias="return")
    orderMatters: bool
    inPlace: bool


class GeneratedTestCase(BaseModel):
    id: str
    label: str                    # e.g. "Edge — empty array"
    inputData: Dict[str, Any]
    expectedOutput: Dict[str, Any]
    is_hidden: bool
    note: str                     # why this testcase matters


class GeneratedMeta(BaseModel):
    mode: str
    leetcode_number: Optional[int] = None
    generated_at: str
    model_version: str
    generation_duration_ms: int
    total_testcases: int
    visible_testcases: int
    hidden_testcases: int


class GenerateTestcasesResponse(BaseModel):
    title: str
    description: str
    difficulty: str               # EASY | MEDIUM | HARD
    tags: List[str]
    time_limit_minutes: int
    optimal_time_complexity: str
    optimal_space_complexity: str
    function_meta: FunctionMeta
    starter_code: str             # Python
    testcases: List[GeneratedTestCase]
    meta: GeneratedMeta
    warning: Optional[str] = None
