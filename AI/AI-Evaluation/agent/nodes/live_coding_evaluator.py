from llm import call_structured
from agent.state import AgentState
from agent.prompts.live_coding import build_live_coding_prompt
from models.outputs import LiveCodingOutput


def live_coding_evaluator_node(state: AgentState) -> dict:
    """
    Call Gemini via Instructor to evaluate a live coding submission.
    Instructor enforces the LiveCodingOutput Pydantic schema as structured output.
    """
    validated_input = state["validated_input"]
    retry_count: int = state.get("retry_count", 0)
    previous_error: str = state.get("evaluation_error", "") or ""

    retry_instruction = ""
    if retry_count > 0 and previous_error:
        retry_instruction = previous_error

    try:
        prompt = build_live_coding_prompt(validated_input, retry_instruction=retry_instruction)
        result: LiveCodingOutput = call_structured(prompt, LiveCodingOutput)

        return {
            "raw_output": result,
            "last_prompt": prompt,
            "evaluation_error": None,
        }

    except Exception as exc:
        return {
            "raw_output": None,
            "evaluation_error": f"LiveCodingEvaluator error: {exc}",
        }
