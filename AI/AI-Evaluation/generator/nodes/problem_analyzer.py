from typing import List, Literal

from pydantic import BaseModel

from llm import call_structured
from generator.leetcode_fetcher import (
    LeetCodeFetchError,
    LeetCodeNotFoundError,
    LeetCodePremiumError,
    fetch_leetcode_problem,
)
from generator.prompts.problem_analysis import build_problem_analysis_prompt
from generator.state import GeneratorState
from models.generator_inputs import GenerateTestcasesRequest


# ── Internal Pydantic model used with Instructor ──────────────────────────────

class _ParamMeta(BaseModel):
    name: str
    type: str


class ProblemAnalysisOutput(BaseModel):
    title: str
    description: str
    difficulty: Literal["EASY", "MEDIUM", "HARD"]
    tags: List[str]
    time_limit_minutes: int
    optimal_time_complexity: str
    optimal_space_complexity: str
    fn: str
    params: List[_ParamMeta]
    return_type: str
    order_matters: bool
    in_place: bool
    starter_code: str
    constraints: List[str]
    edge_case_hints: List[str]


# ── Node ──────────────────────────────────────────────────────────────────────

def problem_analyzer_node(state: GeneratorState) -> dict:
    """
    For leetcode mode: first fetch the real problem from LeetCode's GraphQL
    endpoint, then call Gemini via Instructor to analyse it and produce
    complete technical metadata (title, description, functionMeta, complexity...).

    For custom mode: skip fetching and analyse the user-provided description.
    """
    req: GenerateTestcasesRequest = state["validated_input"]
    leetcode_problem: dict | None = None

    # ── Step 1: fetch real LeetCode data if applicable ────────────────────────
    if req.mode == "leetcode":
        try:
            lp = fetch_leetcode_problem(req.leetcode_url)
            leetcode_problem = lp.model_dump()
        except LeetCodeNotFoundError as exc:
            return {
                "final_output": {
                    "error": "leetcode_not_found",
                    "detail": str(exc),
                }
            }
        except LeetCodePremiumError as exc:
            return {
                "final_output": {
                    "error": "leetcode_premium",
                    "detail": str(exc),
                }
            }
        except (LeetCodeFetchError, ValueError) as exc:
            return {
                "final_output": {
                    "error": "leetcode_fetch_failed",
                    "detail": str(exc),
                }
            }

    # ── Step 2: LLM analysis ──────────────────────────────────────────────────
    try:
        prompt = build_problem_analysis_prompt(req, leetcode_problem)
        result: ProblemAnalysisOutput = call_structured(prompt, ProblemAnalysisOutput)

        # ── Step 3: override LLM output with authoritative sources ────────────
        if req.mode == "leetcode" and leetcode_problem:
            # LeetCode data is ground truth — don't let the LLM drift
            result.title = leetcode_problem["title"]
            result.description = leetcode_problem["description"]
            result.difficulty = leetcode_problem["difficulty"]
            if leetcode_problem.get("tags"):
                result.tags = leetcode_problem["tags"][:5]
            if leetcode_problem.get("constraints"):
                result.constraints = leetcode_problem["constraints"]
        elif req.mode == "custom":
            if req.difficulty:
                result.difficulty = req.difficulty.upper()
            if req.tags:
                result.tags = req.tags
            if req.optimal_time_complexity:
                result.optimal_time_complexity = req.optimal_time_complexity
            if req.optimal_space_complexity:
                result.optimal_space_complexity = req.optimal_space_complexity

        return {
            "leetcode_problem": leetcode_problem,
            "problem_analysis": result.model_dump(),
            "generation_error": None,
        }

    except Exception as exc:
        return {
            "leetcode_problem": leetcode_problem,
            "problem_analysis": None,
            "final_output": {
                "error": "problem_analysis_failed",
                "detail": f"ProblemAnalyzer error: {exc}",
            },
        }
