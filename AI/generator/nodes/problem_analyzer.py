from typing import List, Literal

from pydantic import BaseModel, field_validator, model_validator

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


# ── Schema-allowed type tokens ────────────────────────────────────────────────
# These mirror the judge-service drivers' supported types (UniversalJavaDriver,
# UniversalPythonDriver, UniversalJsDriver, UniversalCppDriver). Any token not
# in this set CANNOT be serialized to stdin by judge-service.StdinBuilder /
# TypeSerializer, so an "invented" type from the LLM (e.g. `float`,
# `Map<String,Integer>`, `Set<Integer>`) would WA / RE on every case.
#
# Keep this list in lockstep with:
#   services/judge-service/src/main/java/com/interview/judge/codebuilder/TypeSerializer.java
#   services/judge-service/src/main/java/com/interview/judge/codebuilder/CodeBuilder.cppTypeFor
#   services/judge-service/src/main/resources/drivers/UniversalJavaDriver.java getJavaType
_ALLOWED_PARAM_TYPES = frozenset({
    # Primitives + boxed
    "int", "long", "double", "boolean",
    "Integer", "Long", "Double", "Boolean",
    "char", "Character",
    "String", "string",
    # 1-D arrays
    "int[]", "long[]", "double[]", "String[]", "string[]",
    # 2-D arrays
    "int[][]", "char[][]", "String[][]",
    # Java-flavoured generics (kept for LeetCode-canonical signatures)
    "List<Integer>", "List<String>",
    "List<List<Integer>>", "List<List<String>>",
    # Reference structures
    "TreeNode", "ListNode",
})

# Only `return_type` may carry "void"; params may not.
_ALLOWED_RETURN_TYPES = _ALLOWED_PARAM_TYPES | {"void"}


# ── Internal Pydantic model used with Instructor ──────────────────────────────

class _ParamMeta(BaseModel):
    name: str
    type: str

    @field_validator("type")
    @classmethod
    def _check_param_type(cls, v: str) -> str:
        if v not in _ALLOWED_PARAM_TYPES:
            raise ValueError(
                f"param type '{v}' is not supported by the judge driver. "
                f"Pick the closest match from the allowed set: "
                f"{sorted(_ALLOWED_PARAM_TYPES)}. 'void' is only valid as a "
                "return_type."
            )
        return v


class _StarterCode(BaseModel):
    python: str
    java: str
    cpp: str
    javascript: str


class ProblemAnalysisOutput(BaseModel):
    title: str
    description: str
    difficulty: Literal["EASY", "MEDIUM", "HARD"]
    tags: List[str]
    optimal_time_complexity: str
    optimal_space_complexity: str
    fn: str
    params: List[_ParamMeta]
    return_type: str
    order_matters: bool
    in_place: bool
    starter_code: _StarterCode
    constraints: List[str]
    edge_case_hints: List[str]

    @field_validator("return_type")
    @classmethod
    def _check_return_type(cls, v: str) -> str:
        if v not in _ALLOWED_RETURN_TYPES:
            raise ValueError(
                f"return_type '{v}' is not supported by the judge driver. "
                f"Pick the closest match from: {sorted(_ALLOWED_RETURN_TYPES)}."
            )
        return v

    @model_validator(mode="after")
    def _enforce_in_place_void(self) -> "ProblemAnalysisOutput":
        """
        Driver contract (judge-service UniversalJavaDriver, UniversalPythonDriver,
        UniversalJsDriver, UniversalCppDriver): when ``inPlace=true`` the driver
        prints ``params[0]`` after mutation and IGNORES the function's return
        value. For testcase-vs-driver consistency the return type therefore MUST
        be ``"void"`` — otherwise the candidate's returned scalar is silently
        dropped and ``expectedOutput.result`` (sized to the wrong type) WAs on
        every case.

        A LeetCode problem like ``removeElement`` (returns int ``k`` AND mutates
        ``nums``) does not fit the pure-mutation mould — model it with
        ``in_place=false`` and ``return_type="int"`` so the judge compares
        against the returned ``k``. The mutation itself is no longer verified,
        which we accept (judge is single-output; the interview-grade signal
        from ``k`` is enough).
        """
        if self.in_place and self.return_type != "void":
            raise ValueError(
                f"in_place=true requires return_type='void' (got "
                f"'{self.return_type}'). The judge's driver discards the return "
                "value when in_place is true and compares against the mutated "
                "first argument only. If the canonical signature also returns a "
                "meaningful value (e.g. Remove Element / Remove Duplicates "
                "return int k), set in_place=false and return_type to that "
                "type — the candidate is still expected to mutate but it will "
                "not be verified."
            )
        return self


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
            # LeetCode data is ground truth for the language-neutral fields —
            # don't let the LLM drift on the title, difficulty or tags.
            #
            # description / constraints are intentionally NOT overridden: the
            # prompt makes the model translate them into Vietnamese while
            # preserving every identifier / number / expression verbatim, so
            # copying the English back here would defeat that.
            result.title = leetcode_problem["title"]
            result.difficulty = leetcode_problem["difficulty"]
            if leetcode_problem.get("tags"):
                result.tags = leetcode_problem["tags"][:5]
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
