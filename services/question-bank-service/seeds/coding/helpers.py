"""Shared input generators for problem definitions.

Every generator takes a seeded `random.Random` so the whole seed file is
reproducible (same questions/ids/testcases on every run).
"""
from __future__ import annotations

import string


def ints(rng, n, lo, hi):
    return [rng.randint(lo, hi) for _ in range(n)]


def unique_ints(rng, n, lo, hi):
    pool = list(range(lo, hi + 1))
    rng.shuffle(pool)
    return pool[:n]


def word(rng, n, alphabet="abcdefghijklmnopqrstuvwxyz"):
    return "".join(rng.choice(alphabet) for _ in range(n))


def lower(rng, n):
    return word(rng, n, "abcdefghijklmnopqrstuvwxyz")


def tree_array(rng, n, lo, hi, null_ratio=0.25):
    """Random level-order tree array (with some nulls), root never null."""
    if n == 0:
        return []
    out = [rng.randint(lo, hi)]
    for _ in range(n - 1):
        if rng.random() < null_ratio:
            out.append(None)
        else:
            out.append(rng.randint(lo, hi))
    while out and out[-1] is None:
        out.pop()
    return out


def bst_array(rng, n, lo, hi):
    """Level-order array of a *valid* BST built from a random sorted sample."""
    vals = sorted(rng.sample(range(lo, hi + 1), n)) if n else []

    def build(a):
        if not a:
            return None
        m = len(a) // 2
        return (a[m], build(a[:m]), build(a[m + 1:]))

    root = build(vals)
    # serialize to level-order with trailing-null trim
    if not root:
        return []
    out, q = [], [root]
    while q:
        node = q.pop(0)
        if node is None:
            out.append(None)
            continue
        out.append(node[0])
        q.append(node[1])
        q.append(node[2])
    while out and out[-1] is None:
        out.pop()
    return out


def random_tuple(rng, max_nodes, lo, hi, null_ratio=0.3):
    """Random binary tree as nested tuples (val, left, right) or None."""
    if max_nodes <= 0 or rng.random() < null_ratio:
        return None
    return (
        rng.randint(lo, hi),
        random_tuple(rng, max_nodes // 2, lo, hi, null_ratio),
        random_tuple(rng, max_nodes // 2, lo, hi, null_ratio),
    )


def mirror(t):
    if t is None:
        return None
    return (t[0], mirror(t[2]), mirror(t[1]))


def level_serialize(root):
    """Tuple tree → level-order array with trailing nulls trimmed (judge format)."""
    if root is None:
        return []
    out, q = [], [root]
    while q:
        n = q.pop(0)
        if n is None:
            out.append(None)
            continue
        out.append(n[0])
        q.append(n[1])
        q.append(n[2])
    while out and out[-1] is None:
        out.pop()
    return out


ALPHA = string.ascii_lowercase
