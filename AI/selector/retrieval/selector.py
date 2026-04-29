"""End-to-end /next-question pipeline:

  request → weakness profile → embedding → SQL hard filter → cosine rerank → top-1
"""
from __future__ import annotations

import logging
from typing import List, Optional

import config
from llm import embed_text
from models.selector.api import (
    NextQuestionRequest,
    NextQuestionResponse,
    RetrievalCandidate,
    RetrievalMeta,
)
from models.selector.snapshot import QuestionSnapshot
from selector.repository import question_index as repo
from selector.retrieval.weakness_profile import WeaknessProfile, build_profile

logger = logging.getLogger(__name__)


_DIFFICULTY_LADDER = ["EASY", "MEDIUM", "HARD"]


def _adjust_difficulty(hint: Optional[str], previous_score: Optional[int]) -> Optional[str]:
    """If the caller passed a hint, honour it. Otherwise nudge based on score."""
    if hint:
        return hint
    if previous_score is None:
        return None
    if previous_score >= 75:
        return "HARD" if previous_score >= 90 else "MEDIUM"
    if previous_score < 50:
        return "EASY"
    return "MEDIUM"


def _row_to_snapshot(row: dict) -> QuestionSnapshot:
    return QuestionSnapshot(
        id=row["id"],
        type=row["type"],
        text=row["text"],
        difficulty=row.get("difficulty") or "",
        tags=list(row.get("tags") or []),
        competency=row.get("competency"),
        expected_signals=list(row.get("expected_signals") or []),
        domain=row.get("domain"),
        target_roles=list(row.get("target_roles") or []),
        key_concepts=list(row.get("key_concepts") or []),
        depth_expected=row.get("depth_expected"),
    )


def _rationale(profile: WeaknessProfile, picked: QuestionSnapshot) -> str:
    if profile.strategy == "first_turn":
        if picked.type == "BEHAVIORAL":
            return f"Câu hỏi mở đầu hành vi (năng lực: {picked.competency or 'tổng quát'})."
        return f"Câu hỏi mở đầu chuyên môn (lĩnh vực: {picked.domain or 'tổng quát'})."

    bits: List[str] = []
    if profile.weak_signals:
        bits.append("đào sâu tín hiệu yếu: " + ", ".join(profile.weak_signals[:3]))
    if profile.missing_concepts:
        bits.append("kiểm tra khái niệm còn thiếu: " + ", ".join(profile.missing_concepts[:3]))
    if profile.competency_hint and picked.competency == profile.competency_hint:
        bits.append(f"giữ trong năng lực {profile.competency_hint}")
    if profile.domain_hint and picked.domain == profile.domain_hint:
        bits.append(f"giữ trong lĩnh vực {profile.domain_hint}")
    if not bits:
        bits.append("ngữ nghĩa gần nhất với điểm yếu vừa phát hiện")
    return "Câu kế tiếp được chọn để " + "; ".join(bits) + "."


async def select_next_question(req: NextQuestionRequest) -> NextQuestionResponse:
    profile = build_profile(
        interview_type=req.interview_type,
        previous_evaluation=req.previous_evaluation,
        competency=req.constraints.competency,
        domain=req.constraints.domain,
        target_role=req.constraints.target_role,
    )

    query_embedding = embed_text(profile.text)

    difficulty = _adjust_difficulty(req.constraints.difficulty_hint, profile.overall_score)

    rows = await repo.hybrid_search(
        interview_type=req.interview_type,
        query_embedding=query_embedding,
        asked_question_ids=req.asked_question_ids,
        competency=profile.competency_hint if req.interview_type == "BEHAVIORAL" else None,
        domain=profile.domain_hint if req.interview_type == "CORE_CONCEPTUAL" else None,
        target_role=req.constraints.target_role,
        difficulty=difficulty,
        limit=config.TOP_K,
    )

    # Fall back: drop the difficulty filter if it eliminated everything.
    if not rows and difficulty:
        logger.info("No rows under difficulty=%s; retrying without difficulty filter", difficulty)
        rows = await repo.hybrid_search(
            interview_type=req.interview_type,
            query_embedding=query_embedding,
            asked_question_ids=req.asked_question_ids,
            competency=profile.competency_hint if req.interview_type == "BEHAVIORAL" else None,
            domain=profile.domain_hint if req.interview_type == "CORE_CONCEPTUAL" else None,
            target_role=req.constraints.target_role,
            difficulty=None,
            limit=config.TOP_K,
        )

    # Final fallback: drop competency / domain hints too.
    if not rows and (profile.competency_hint or profile.domain_hint):
        logger.info("No rows under hint filters; retrying with type+role only")
        rows = await repo.hybrid_search(
            interview_type=req.interview_type,
            query_embedding=query_embedding,
            asked_question_ids=req.asked_question_ids,
            target_role=req.constraints.target_role,
            difficulty=None,
            limit=config.TOP_K,
        )

    if not rows:
        raise RuntimeError(
            "No candidate question found after all fallbacks. "
            "Check that the index is populated and asked_question_ids is not exhaustive."
        )

    top = rows[0]
    picked = _row_to_snapshot(top)

    candidates = [
        RetrievalCandidate(
            question_id=row["id"],
            similarity=float(1.0 - row["distance"]),
            competency=row.get("competency"),
            domain=row.get("domain"),
            difficulty=row.get("difficulty"),
        )
        for row in rows
    ]

    return NextQuestionResponse(
        session_id=req.session_id,
        question_id=picked.id,
        question_snapshot=picked,
        rationale=_rationale(profile, picked),
        retrieval_meta=RetrievalMeta(
            strategy=profile.strategy,
            candidates_considered=len(rows),
            top_candidates=candidates,
        ),
    )
