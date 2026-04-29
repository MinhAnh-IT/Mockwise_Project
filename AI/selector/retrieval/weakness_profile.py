"""Convert an evaluation-graph output into a weakness profile.

The profile is what we embed and feed into pgvector. It captures *what
this candidate is weak at*, expressed in the same Vietnamese vocabulary
we use when embedding questions, so cosine similarity surfaces questions
that probe those gaps.

Rule-based for now — no extra LLM call per /next-question.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class WeaknessProfile:
    strategy: str  # "first_turn" | "exploit_weakness"
    text: str
    # Optional structured constraints derived from the evaluation. None means
    # "do not constrain on this dimension".
    competency_hint: Optional[str] = None
    domain_hint: Optional[str] = None
    weak_signals: List[str] = field(default_factory=list)
    missing_concepts: List[str] = field(default_factory=list)
    overall_score: Optional[int] = None


def _lowest_score_dimensions(scores: Dict[str, Any], n: int = 2) -> List[str]:
    """Return the names of the n lowest-scoring dimensions."""
    items: List[tuple[str, int]] = []
    for dim_name, dim in (scores or {}).items():
        if isinstance(dim, dict) and isinstance(dim.get("score"), (int, float)):
            items.append((dim_name, int(dim["score"])))
    items.sort(key=lambda x: x[1])
    return [name for name, _ in items[:n]]


def _behavioral_profile(eval_out: Dict[str, Any], constraint_competency: Optional[str]) -> WeaknessProfile:
    scores = eval_out.get("scores", {}) or {}
    signal_coverage = eval_out.get("signal_coverage", []) or []
    red_flags = eval_out.get("red_flags", []) or []
    overall = eval_out.get("overall_score")

    weak_signals: List[str] = [
        s.get("signal_name") for s in signal_coverage
        if isinstance(s, dict) and s.get("detected") is False and s.get("signal_name")
    ]
    weak_dims = _lowest_score_dimensions(scores, n=2)
    high_red_flags = [
        rf.get("type") for rf in red_flags
        if isinstance(rf, dict) and rf.get("severity") == "high" and rf.get("type")
    ]

    parts: List[str] = [
        "Mục tiêu: chọn câu hỏi hành vi tiếp theo nhằm khai thác điểm yếu của ứng viên.",
    ]
    if constraint_competency:
        parts.append(f"Năng lực cần đào sâu: {constraint_competency}.")
    if weak_signals:
        parts.append("Tín hiệu hành vi chưa thể hiện: " + "; ".join(weak_signals) + ".")
    if weak_dims:
        parts.append("Khía cạnh điểm thấp nhất: " + ", ".join(weak_dims) + ".")
    if high_red_flags:
        parts.append("Cờ đỏ nghiêm trọng: " + ", ".join(high_red_flags) + ".")
    if overall is not None:
        parts.append(f"Điểm tổng hiện tại: {overall}/100.")

    return WeaknessProfile(
        strategy="exploit_weakness",
        text="\n".join(parts),
        competency_hint=constraint_competency,
        weak_signals=weak_signals,
        overall_score=int(overall) if isinstance(overall, (int, float)) else None,
    )


def _conceptual_profile(eval_out: Dict[str, Any], constraint_domain: Optional[str]) -> WeaknessProfile:
    scores = eval_out.get("scores", {}) or {}
    concept_coverage = eval_out.get("concept_coverage", []) or []
    misconceptions = eval_out.get("misconceptions", []) or []
    level_calibration = eval_out.get("level_calibration", {}) or {}
    overall = eval_out.get("overall_score")

    missing_concepts: List[str] = [
        c.get("concept_name") for c in concept_coverage
        if isinstance(c, dict)
        and (c.get("mentioned") is False or c.get("correct") is False)
        and c.get("concept_name")
    ]
    weak_dims = _lowest_score_dimensions(scores, n=2)
    misconception_excerpts = [
        m.get("claim") for m in misconceptions
        if isinstance(m, dict) and m.get("claim")
    ][:3]

    parts: List[str] = [
        "Mục tiêu: chọn câu hỏi chuyên môn tiếp theo nhằm khai thác lỗ hổng kiến thức.",
    ]
    if constraint_domain:
        parts.append(f"Lĩnh vực cần đào sâu: {constraint_domain}.")
    if missing_concepts:
        parts.append(
            "Khái niệm còn thiếu hoặc trả lời sai: " + "; ".join(missing_concepts) + "."
        )
    if misconception_excerpts:
        parts.append(
            "Quan niệm sai cần đính chính: " + "; ".join(misconception_excerpts) + "."
        )
    if weak_dims:
        parts.append("Khía cạnh điểm thấp nhất: " + ", ".join(weak_dims) + ".")
    gap = level_calibration.get("gap")
    if gap:
        parts.append(f"Khoảng cách so với độ sâu kỳ vọng: {gap}")
    if overall is not None:
        parts.append(f"Điểm tổng hiện tại: {overall}/100.")

    return WeaknessProfile(
        strategy="exploit_weakness",
        text="\n".join(parts),
        domain_hint=constraint_domain,
        missing_concepts=missing_concepts,
        overall_score=int(overall) if isinstance(overall, (int, float)) else None,
    )


def _first_turn_profile(
    interview_type: str,
    competency: Optional[str],
    domain: Optional[str],
    target_role: Optional[str],
) -> WeaknessProfile:
    parts: List[str] = ["Mục tiêu: chọn câu hỏi mở đầu cho buổi phỏng vấn."]
    if interview_type == "BEHAVIORAL":
        parts.append("Loại câu hỏi: hành vi (behavioral).")
        if competency:
            parts.append(f"Năng lực cần đánh giá: {competency}.")
        else:
            parts.append("Khám phá đa năng lực: ownership, teamwork, communication.")
    else:
        parts.append("Loại câu hỏi: kiến thức chuyên môn (core conceptual).")
        if domain:
            parts.append(f"Lĩnh vực: {domain}.")
        else:
            parts.append("Khái quát các lĩnh vực phổ biến cho vai trò.")
    if target_role:
        parts.append(f"Vai trò ứng viên: {target_role}.")
    return WeaknessProfile(
        strategy="first_turn",
        text="\n".join(parts),
        competency_hint=competency,
        domain_hint=domain,
    )


def build_profile(
    *,
    interview_type: str,
    previous_evaluation: Optional[Dict[str, Any]],
    competency: Optional[str],
    domain: Optional[str],
    target_role: Optional[str],
) -> WeaknessProfile:
    if previous_evaluation is None:
        return _first_turn_profile(interview_type, competency, domain, target_role)

    if interview_type == "BEHAVIORAL":
        return _behavioral_profile(previous_evaluation, competency)
    return _conceptual_profile(previous_evaluation, domain)
