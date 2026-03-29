import instructor
from google import genai

import config
from agent.state import AgentState
from agent.prompts.conceptual import build_conceptual_prompt
from models.outputs import ConceptualOutput


def conceptual_evaluator_node(state: AgentState) -> dict:
    """
    Call Gemini via Instructor to evaluate a conceptual interview answer.
    Instructor enforces the ConceptualOutput Pydantic schema as structured output.
    """
    validated_input = state["validated_input"]
    retry_count: int = state.get("retry_count", 0)
    previous_error: str = state.get("evaluation_error", "") or ""

    retry_instruction = ""
    if retry_count > 0 and previous_error:
        retry_instruction = previous_error

    try:
        prompt = build_conceptual_prompt(validated_input, retry_instruction=retry_instruction)

        client = instructor.from_genai(
            genai.Client(api_key=config.GOOGLE_API_KEY),
        )
        result: ConceptualOutput = client.chat.completions.create(
            model=config.MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            response_model=ConceptualOutput,
        )

        return {
            "raw_output": result,
            "last_prompt": prompt,
            "evaluation_error": None,
        }

    except Exception as exc:
        return {
            "raw_output": None,
            "evaluation_error": f"ConceptualEvaluator error: {exc}",
        }
