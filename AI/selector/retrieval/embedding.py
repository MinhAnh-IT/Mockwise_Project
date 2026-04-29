"""Build the canonical embedding text for a question snapshot.

The embedded text is what determines what "this question is about" in the
vector space. We deliberately include the structured metadata (competency,
domain, key_concepts, expected_signals) alongside the natural-language text
so paraphrased queries about a competency or concept rank closer to the
matching question.

For Vietnamese-only content we keep field labels in Vietnamese so they
share the embedding space with the candidate's transcript.
"""
from __future__ import annotations

from models.selector.snapshot import QuestionSnapshot


def build_question_embedding_text(snapshot: QuestionSnapshot) -> str:
    parts: list[str] = []

    if snapshot.type == "BEHAVIORAL":
        parts.append("Loại câu hỏi: phỏng vấn hành vi (behavioral).")
        if snapshot.competency:
            parts.append(f"Năng lực đánh giá: {snapshot.competency}.")
        if snapshot.expected_signals:
            parts.append(
                "Tín hiệu kỳ vọng: " + "; ".join(snapshot.expected_signals) + "."
            )
    else:  # CORE_CONCEPTUAL
        parts.append("Loại câu hỏi: kiến thức chuyên môn (core conceptual).")
        if snapshot.domain:
            parts.append(f"Lĩnh vực: {snapshot.domain}.")
        if snapshot.target_roles:
            parts.append("Vai trò mục tiêu: " + ", ".join(snapshot.target_roles) + ".")
        if snapshot.key_concepts:
            parts.append(
                "Khái niệm trọng tâm: " + "; ".join(snapshot.key_concepts) + "."
            )
        if snapshot.depth_expected:
            parts.append(f"Độ sâu kỳ vọng: {snapshot.depth_expected}.")

    if snapshot.difficulty:
        parts.append(f"Độ khó: {snapshot.difficulty}.")

    parts.append(f"Câu hỏi: {snapshot.text}")
    return "\n".join(parts)
