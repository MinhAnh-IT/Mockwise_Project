"""
Standalone module to fetch a LeetCode problem's structured data from its
public GraphQL endpoint, given a problem URL or slug.

Usage:
    from generator.leetcode_fetcher import fetch_leetcode_problem

    data = fetch_leetcode_problem("https://leetcode.com/problems/two-sum/")
    print(data.title, data.difficulty)
    print(data.description)
    for ex in data.examples:
        print(ex.input, "→", ex.output)
"""

from __future__ import annotations

import re
from html import unescape
from html.parser import HTMLParser
from typing import List, Optional

import httpx
from pydantic import BaseModel


LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql/"

_QUESTION_DATA_QUERY = """
query questionData($titleSlug: String!) {
  question(titleSlug: $titleSlug) {
    questionFrontendId
    title
    titleSlug
    difficulty
    content
    topicTags { slug name }
    codeSnippets { lang langSlug code }
    exampleTestcases
    hints
    isPaidOnly
  }
}
""".strip()


# ── Public data models ────────────────────────────────────────────────────────

class LeetCodeExample(BaseModel):
    input: str
    output: str
    explanation: Optional[str] = None


class LeetCodeProblem(BaseModel):
    number: int
    title: str
    slug: str
    difficulty: str                      # "EASY" | "MEDIUM" | "HARD"
    description: str                     # full plain-text description
    examples: List[LeetCodeExample]
    constraints: List[str]
    follow_up: Optional[str] = None
    tags: List[str]
    hints: List[str]
    starter_code_python: Optional[str] = None
    starter_code_java: Optional[str] = None
    starter_code_cpp: Optional[str] = None
    starter_code_javascript: Optional[str] = None
    sample_testcases_raw: Optional[str] = None   # raw "exampleTestcases" field
    is_premium: bool


# ── Exceptions ────────────────────────────────────────────────────────────────

class LeetCodeFetchError(Exception):
    """Base error for LeetCode fetch failures."""


class LeetCodeNotFoundError(LeetCodeFetchError):
    """Problem slug does not exist."""


class LeetCodePremiumError(LeetCodeFetchError):
    """Problem is premium and cannot be fetched without auth."""


# ── HTML → text helper (no BeautifulSoup dependency) ──────────────────────────

class _TextExtractor(HTMLParser):
    """Strip HTML tags while keeping paragraph / list-item line breaks."""

    _BLOCK_TAGS = {"p", "div", "br", "li", "ul", "ol", "pre", "h1", "h2", "h3", "h4"}

    def __init__(self) -> None:
        super().__init__()
        self._parts: List[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag == "sup":
            self._parts.append("^")
        elif tag == "sub":
            self._parts.append("_")
        elif tag in self._BLOCK_TAGS:
            self._parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in self._BLOCK_TAGS:
            self._parts.append("\n")

    def handle_data(self, data: str) -> None:
        self._parts.append(data)

    def get_text(self) -> str:
        raw = "".join(self._parts)
        raw = unescape(raw)
        # collapse 3+ newlines to 2, strip trailing spaces per line
        lines = [ln.rstrip() for ln in raw.splitlines()]
        text = "\n".join(lines)
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()


def _html_to_text(html: str) -> str:
    parser = _TextExtractor()
    parser.feed(html)
    return parser.get_text()


# ── Slug / URL helpers ────────────────────────────────────────────────────────

_SLUG_RE = re.compile(r"/problems/([a-z0-9\-]+)")


def extract_slug(url_or_slug: str) -> str:
    """Accept either a full LeetCode URL or a bare slug."""
    s = url_or_slug.strip()
    match = _SLUG_RE.search(s)
    if match:
        return match.group(1)
    # treat as bare slug (validate: lowercase letters / digits / hyphens)
    if re.fullmatch(r"[a-z0-9\-]+", s):
        return s
    raise ValueError(f"Could not extract LeetCode slug from: {url_or_slug!r}")


# ── Parse description into sections ───────────────────────────────────────────

_EXAMPLE_HEADER_RE = re.compile(r"^\s*Example\s*\d+\s*:\s*$", re.MULTILINE)
_CONSTRAINTS_HEADER_RE = re.compile(r"^\s*Constraints\s*:\s*$", re.MULTILINE)
_FOLLOWUP_HEADER_RE = re.compile(r"^\s*Follow[- ]?up\s*:?\s*", re.MULTILINE | re.IGNORECASE)


def _parse_example_block(block: str) -> Optional[LeetCodeExample]:
    """Parse a block after 'Example N:' into Input/Output/Explanation."""
    inp = re.search(r"Input\s*:\s*(.*?)(?=\n\s*Output\s*:|\Z)", block, re.S | re.IGNORECASE)
    out = re.search(
        r"Output\s*:\s*(.*?)(?=\n\s*Explanation\s*:|\Z)", block, re.S | re.IGNORECASE
    )
    expl = re.search(r"Explanation\s*:\s*(.*)", block, re.S | re.IGNORECASE)

    if not inp or not out:
        return None
    return LeetCodeExample(
        input=inp.group(1).strip(),
        output=out.group(1).strip(),
        explanation=expl.group(1).strip() if expl else None,
    )


def _split_sections(text: str) -> tuple[str, List[LeetCodeExample], List[str], Optional[str]]:
    """Return (description_body, examples, constraints, follow_up)."""
    # Split on the first "Example 1:" header
    ex_matches = list(_EXAMPLE_HEADER_RE.finditer(text))
    cons_match = _CONSTRAINTS_HEADER_RE.search(text)
    fu_match = _FOLLOWUP_HEADER_RE.search(text)

    # Description body = everything before the first Example (or Constraints, if none)
    body_end = (
        ex_matches[0].start()
        if ex_matches
        else (cons_match.start() if cons_match else len(text))
    )
    body = text[:body_end].strip()

    # Examples
    examples: List[LeetCodeExample] = []
    for i, m in enumerate(ex_matches):
        start = m.end()
        end = ex_matches[i + 1].start() if i + 1 < len(ex_matches) else (
            cons_match.start() if cons_match else (fu_match.start() if fu_match else len(text))
        )
        parsed = _parse_example_block(text[start:end])
        if parsed:
            examples.append(parsed)

    # Constraints
    constraints: List[str] = []
    if cons_match:
        cons_end = fu_match.start() if fu_match and fu_match.start() > cons_match.end() else len(text)
        cons_block = text[cons_match.end():cons_end]
        for line in cons_block.splitlines():
            # strip bullet markers but preserve '-' (used for negative numbers)
            stripped = line.lstrip(" \t•*")
            if stripped.startswith("- "):
                stripped = stripped[2:]
            stripped = stripped.strip()
            if stripped:
                constraints.append(stripped)

    # Follow-up
    follow_up = None
    if fu_match:
        follow_up = text[fu_match.end():].strip() or None

    return body, examples, constraints, follow_up


# ── Main fetcher ──────────────────────────────────────────────────────────────

def fetch_leetcode_problem(
    url_or_slug: str,
    *,
    timeout: float = 10.0,
    session_cookie: Optional[str] = None,
) -> LeetCodeProblem:
    """
    Fetch structured data for a LeetCode problem.

    Args:
        url_or_slug: e.g. "https://leetcode.com/problems/two-sum/" or "two-sum"
        timeout:     request timeout in seconds
        session_cookie: optional LEETCODE_SESSION cookie for premium problems

    Raises:
        LeetCodeNotFoundError: slug does not exist
        LeetCodePremiumError:  problem is premium and content is not accessible
        LeetCodeFetchError:    any other network / parsing failure
    """
    slug = extract_slug(url_or_slug)

    headers = {
        "Content-Type": "application/json",
        "Referer": f"https://leetcode.com/problems/{slug}/",
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/124.0.0.0 Safari/537.36"
        ),
    }
    cookies = {"LEETCODE_SESSION": session_cookie} if session_cookie else None

    payload = {
        "operationName": "questionData",
        "variables": {"titleSlug": slug},
        "query": _QUESTION_DATA_QUERY,
    }

    try:
        with httpx.Client(timeout=timeout, cookies=cookies) as client:
            resp = client.post(LEETCODE_GRAPHQL_URL, json=payload, headers=headers)
            resp.raise_for_status()
            body = resp.json()
    except httpx.HTTPError as exc:
        raise LeetCodeFetchError(f"Network error while fetching {slug!r}: {exc}") from exc
    except ValueError as exc:
        raise LeetCodeFetchError(f"Invalid JSON from LeetCode for {slug!r}: {exc}") from exc

    if body.get("errors"):
        raise LeetCodeFetchError(f"GraphQL errors for {slug!r}: {body['errors']}")

    q = (body.get("data") or {}).get("question")
    if not q:
        raise LeetCodeNotFoundError(f"No such LeetCode problem: {slug!r}")

    if q.get("isPaidOnly") and not q.get("content"):
        raise LeetCodePremiumError(
            f"Problem {slug!r} is premium; provide session_cookie to fetch it."
        )

    content_html = q.get("content") or ""
    plain = _html_to_text(content_html)
    body_text, examples, constraints, follow_up = _split_sections(plain)

    snippets = q.get("codeSnippets") or []
    def _by_slug(slug: str) -> Optional[str]:
        return next((c["code"] for c in snippets if c.get("langSlug") == slug), None)

    return LeetCodeProblem(
        number=int(q["questionFrontendId"]),
        title=q["title"],
        slug=q["titleSlug"],
        difficulty=q["difficulty"].upper(),
        description=body_text,
        examples=examples,
        constraints=constraints,
        follow_up=follow_up,
        tags=[t["slug"] for t in (q.get("topicTags") or [])],
        hints=list(q.get("hints") or []),
        starter_code_python=_by_slug("python3"),
        starter_code_java=_by_slug("java"),
        starter_code_cpp=_by_slug("cpp"),
        starter_code_javascript=_by_slug("javascript"),
        sample_testcases_raw=q.get("exampleTestcases"),
        is_premium=bool(q.get("isPaidOnly")),
    )


# ── CLI smoke-test ────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import json
    import sys

    target = sys.argv[1] if len(sys.argv) > 1 else "https://leetcode.com/problems/two-sum/"
    problem = fetch_leetcode_problem(target)
    print(json.dumps(problem.model_dump(), indent=2, ensure_ascii=False))
