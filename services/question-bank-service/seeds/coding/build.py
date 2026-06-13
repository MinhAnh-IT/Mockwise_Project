#!/usr/bin/env python3
"""Assemble + verify all coding seed problems → coding_questions.json.

  python3 build.py            # build everything
  python3 build.py easy       # only one batch (debugging)
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

from engine import run_build

BATCHES = ["easy", "medium", "hard"]


def load(name):
    mod = __import__(f"problems_{name}")
    return mod.PROBLEMS


def main():
    which = sys.argv[1:] or BATCHES
    problems = []
    for name in which:
        problems.extend(load(name))

    rng = random.Random(20260613)
    out = Path(__file__).resolve().parent / "coding_questions.json"
    run_build(problems, out, rng)


if __name__ == "__main__":
    main()
