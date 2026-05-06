"""Prompt for the cross-question overall reviewer.

The reviewer reads every answer's per-question verdict (already computed
by the per-answer evaluator graph) plus transcripts/code, and produces a
consolidated assessment the candidate sees at the end of the interview.
The qualitative narrative is generated; the numeric `overall_score` is
recomputed deterministically by the output_validator from the per-answer
scores so the LLM cannot drift from the data.
"""
from __future__ import annotations

import json
from typing import Any

from models.session_review import SessionEvaluationPayload


def build_overall_review_prompt(
        payload: SessionEvaluationPayload,
        retry_instruction: str = "",
) -> str:
    answers_json = json.dumps(
        [a.model_dump(by_alias=False) for a in payload.answers],
        ensure_ascii=False,
        indent=2,
    )
    blueprint_json = json.dumps(
        payload.blueprint.model_dump(by_alias=False),
        ensure_ascii=False,
        indent=2,
    )

    retry_block = f"\n\nIMPORTANT — previous attempt was rejected. {retry_instruction}\n" if retry_instruction else ""

    return f"""You are a senior interviewer summarising a finished mock interview.
The candidate has already answered every pinned question; each answer carries
a per-question verdict produced earlier. Your job is the cross-question
narrative — strengths, weaknesses, per-topic coverage, and concrete
recommendations.

INPUT
─────
session_id: {payload.session_id}
target_role: {payload.target_role}
level: {payload.level}
interview_type: {payload.interview_type}

blueprint (the planned topic coverage):
{blueprint_json}

answers (in order, with per-answer verdicts already attached):
{answers_json}

OUTPUT — strict JSON matching the OverallReviewOutput schema
────────────────────────────────────────────────────────────
{{
  "session_id": "{payload.session_id}",
  "overall_score": <float 0..10>,
  "grade": "A" | "B" | "C" | "D" | "F",
  "hire_signal": "strong_yes" | "yes" | "weak_yes" | "no" | "strong_no",
  "summary": "<2-4 sentence overview of how the candidate performed across the whole interview>",
  "strengths": ["<bullet 1>", "<bullet 2>", ...],
  "weaknesses": ["<bullet 1>", "<bullet 2>", ...],
  "per_topic_summary": [
    {{
      "topic_kind": "COMPETENCY" | "DOMAIN",
      "topic_value": "<topic from blueprint>",
      "status": "STRONG" | "ADEQUATE" | "PARTIAL" | "WEAK" | "UNKNOWN" | "NOT_TESTED",
      "comment": "<one-sentence justification>"
    }}
  ],
  "recommendations": ["<actionable next-step 1>", "..."]
}}

GUIDELINES
──────────
1. Iterate every blueprint topic. If no answer covered a topic, mark it
   NOT_TESTED with status comment explaining the coverage gap.
2. For topics with at least one answer, derive `status` from the
   distribution of per-answer verdicts on that topic — STRONG when
   the candidate consistently demonstrated the signal, WEAK when most
   answers were wrong/shallow, UNKNOWN if the candidate opted out
   (NO_ANSWER on every probe).
3. `summary` must be specific to this candidate — do not produce a
   generic template; cite concrete things they said when possible.
4. `strengths` and `weaknesses` should be 2–5 bullets each, grounded in
   actual answers (no hallucinated content).
5. `recommendations` are concrete next-step actions the candidate can
   take to improve (study X, practise Y).
6. Pick `overall_score`, `grade`, `hire_signal` consistent with the
   per-answer scores — but downstream validation will recompute the
   numeric and band, so prioritise narrative quality.{retry_block}
"""
