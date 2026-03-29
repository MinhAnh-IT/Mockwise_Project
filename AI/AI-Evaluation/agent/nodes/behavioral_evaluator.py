import instructor
from google import genai

import config
from agent.state import AgentState
from agent.prompts.behavioral import build_behavioral_prompt
from models.outputs import BehavioralOutput


def behavioral_evaluator_node(state: AgentState) -> dict:
    """
    Call Gemini via Instructor to evaluate a behavioral interview answer.
    Instructor enforces the BehavioralOutput Pydantic schema as structured output.
    """
    validated_input = state["validated_input"]
    retry_count: int = state.get("retry_count", 0)
    previous_error: str = state.get("evaluation_error", "") or ""

    retry_instruction = ""
    if retry_count > 0 and previous_error:
        retry_instruction = previous_error

    try:
        prompt = build_behavioral_prompt(validated_input, retry_instruction=retry_instruction)

        client = instructor.from_genai(
            genai.Client(api_key=config.GOOGLE_API_KEY),
        )
        result: BehavioralOutput = client.chat.completions.create(
            model=config.MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            response_model=BehavioralOutput,
        )

        return {
            "raw_output": result,
            "last_prompt": prompt,
            "evaluation_error": None,
        }

    except Exception as exc:
        return {
            "raw_output": None,
            "evaluation_error": f"BehavioralEvaluator error: {exc}",
        }
