from typing import Literal, List, Optional
from pydantic import BaseModel, model_validator


class GenerateTestcasesRequest(BaseModel):
    mode: Literal["custom", "leetcode"]

    # ── leetcode mode ─────────────────────────────────────────────────────────
    leetcode_url: Optional[str] = None    # e.g. "https://leetcode.com/problems/two-sum/" or "two-sum"

    # ── custom mode ───────────────────────────────────────────────────────────
    title: Optional[str] = None
    description: Optional[str] = None
    difficulty: Optional[str] = None                  # AI infers if missing
    tags: List[str] = []
    optimal_time_complexity: Optional[str] = None     # AI infers if missing
    optimal_space_complexity: Optional[str] = None    # AI infers if missing

    # ── shared ────────────────────────────────────────────────────────────────
    num_testcases: int = 10
    num_hidden: int = 3

    @model_validator(mode="after")
    def validate_by_mode(self):
        if self.mode == "leetcode":
            if not self.leetcode_url:
                raise ValueError(
                    "leetcode_url is required for mode=leetcode "
                    "(e.g. 'https://leetcode.com/problems/two-sum/' or just 'two-sum')"
                )
        if self.mode == "custom":
            if not self.title or not self.description:
                raise ValueError(
                    "title and description are required for mode=custom"
                )
        if self.num_hidden >= self.num_testcases:
            raise ValueError("num_hidden must be less than num_testcases")
        return self
