from typing import Literal, List, Optional
from pydantic import BaseModel, ConfigDict, model_validator
from pydantic.alias_generators import to_camel


class GenerateTestcasesRequest(BaseModel):
    """
    JSON request shape is camelCase ONLY. Python field names stay snake_case
    for Pythonic access on the server side, but client payloads must use the
    camelCase aliases — snake_case keys are rejected.
    """
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=False,
    )

    mode: Literal["custom", "leetcode"]

    # ── leetcode mode ─────────────────────────────────────────────────────────
    # JSON: leetcodeUrl   e.g. "https://leetcode.com/problems/two-sum/" or "two-sum"
    leetcode_url: Optional[str] = None

    # ── custom mode ───────────────────────────────────────────────────────────
    title: Optional[str] = None
    description: Optional[str] = None
    difficulty: Optional[str] = None                  # AI infers if missing
    tags: List[str] = []
    optimal_time_complexity: Optional[str] = None     # JSON: optimalTimeComplexity
    optimal_space_complexity: Optional[str] = None    # JSON: optimalSpaceComplexity

    # ── shared ────────────────────────────────────────────────────────────────
    num_testcases: int = 10                            # JSON: numTestcases
    # Number of testcases shown to the candidate. The remainder
    # (numTestcases - numVisible) are hidden — typically the larger group.
    num_visible: int = 3                               # JSON: numVisible

    @model_validator(mode="after")
    def validate_by_mode(self):
        if self.mode == "leetcode":
            if not self.leetcode_url:
                raise ValueError(
                    "leetcodeUrl is required for mode=leetcode "
                    "(e.g. 'https://leetcode.com/problems/two-sum/' or just 'two-sum')"
                )
        if self.mode == "custom":
            if not self.title or not self.description:
                raise ValueError(
                    "title and description are required for mode=custom"
                )
        if self.num_visible < 1:
            raise ValueError("numVisible must be at least 1")
        if self.num_visible >= self.num_testcases:
            raise ValueError("numVisible must be less than numTestcases (some testcases must remain hidden)")
        return self

    @property
    def num_hidden(self) -> int:
        """Computed: testcases not shown to the candidate."""
        return self.num_testcases - self.num_visible
