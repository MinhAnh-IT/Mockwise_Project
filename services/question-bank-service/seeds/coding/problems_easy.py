"""EASY problems — NeetCode-popular, non-duplicate with the 25 already in prod.

Each problem: meta + python `ref` (defines `class Solution`) + `examples`
(become visible cases) + `gen(rng)` (hidden cases). The engine runs the real
judge driver on every case to derive & verify the expected output.
"""
from __future__ import annotations

from helpers import (ints, unique_ints, lower, tree_array, random_tuple,
                     mirror, level_serialize)

PROBLEMS = []


def _p(**kw):
    PROBLEMS.append(kw)


# ── bit / math ─────────────────────────────────────────────────────────────
_p(
    title="Number of 1 Bits",
    difficulty="EASY",
    tags=["bit-manipulation"],
    description=(
        "Write a function that takes the binary representation of a positive "
        "integer and returns the number of set bits it has (also known as the "
        "Hamming weight)."),
    constraints="- `0 <= n <= 2^31 - 1`",
    time="O(1)", space="O(1)",
    fn="hammingWeight", params=[("n", "int")], **{"return": "int"},
    ref="""
class Solution:
    def hammingWeight(self, n: int) -> int:
        return bin(n).count("1")
""",
    examples=[
        {"input": {"n": 11}, "note": "11 = 1011 (cơ số 2) có 3 bit 1."},
        {"input": {"n": 128}, "note": "128 = 10000000 có đúng 1 bit 1."},
        {"input": {"n": 0}, "note": "0 không có bit 1 nào."},
    ],
    gen=lambda rng: (
        [{"n": v} for v in [1, 2, 3, 7, 255, 256, 1023, 2147483647, 2147483646,
                            1 << 20, (1 << 30) + 1]]
        + [{"n": rng.randint(0, 2147483647)} for _ in range(16)]),
)

_p(
    title="Counting Bits",
    difficulty="EASY",
    tags=["dynamic-programming", "bit-manipulation"],
    description=(
        "Given an integer `n`, return an array `ans` of length `n + 1` such that "
        "for each `i` (0 <= i <= n), `ans[i]` is the number of 1's in the binary "
        "representation of `i`."),
    constraints="- `0 <= n <= 10^5`",
    time="O(n)", space="O(n)",
    fn="countBits", params=[("n", "int")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def countBits(self, n: int):
        dp = [0] * (n + 1)
        for i in range(1, n + 1):
            dp[i] = dp[i >> 1] + (i & 1)
        return dp
""",
    examples=[
        {"input": {"n": 2}, "note": "0->0, 1->1, 2->10 ⇒ [0,1,1]."},
        {"input": {"n": 5}, "note": "[0,1,1,2,1,2]."},
        {"input": {"n": 0}, "note": "Chỉ có số 0 ⇒ [0]."},
    ],
    gen=lambda rng: ([{"n": v} for v in [1, 3, 4, 7, 8, 15, 16, 31, 100, 255]]
                     + [{"n": rng.randint(0, 300)} for _ in range(14)]),
)

# ── arrays / hashing ───────────────────────────────────────────────────────
_p(
    title="Missing Number",
    difficulty="EASY",
    tags=["array", "hash-table", "math", "bit-manipulation"],
    description=(
        "Given an array `nums` containing `n` distinct numbers in the range "
        "`[0, n]`, return the only number in the range that is missing from the "
        "array."),
    constraints="- `n == nums.length`\n- `1 <= n <= 10^4`\n- All numbers are unique.",
    time="O(n)", space="O(1)",
    fn="missingNumber", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def missingNumber(self, nums):
        n = len(nums)
        return n * (n + 1) // 2 - sum(nums)
""",
    examples=[
        {"input": {"nums": [3, 0, 1]}, "note": "n=3, dải [0,3], thiếu 2."},
        {"input": {"nums": [0, 1]}, "note": "n=2, dải [0,2], thiếu 2."},
        {"input": {"nums": [9, 6, 4, 2, 3, 5, 7, 0, 1]}, "note": "thiếu 8."},
    ],
    gen=lambda rng: [_missing_case(rng) for _ in range(22)],
)


def _missing_case(rng):
    n = rng.randint(1, 30)
    miss = rng.randint(0, n)
    nums = [x for x in range(n + 1) if x != miss]
    rng.shuffle(nums)
    return {"nums": nums}


_p(
    title="Single Number",
    difficulty="EASY",
    tags=["array", "bit-manipulation"],
    description=(
        "Given a non-empty array of integers `nums`, every element appears twice "
        "except for one. Find that single one. You must implement a solution with "
        "linear runtime complexity and use only constant extra space."),
    constraints="- `1 <= nums.length <= 3*10^4`\n- Each element appears twice except one.",
    time="O(n)", space="O(1)",
    fn="singleNumber", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def singleNumber(self, nums):
        x = 0
        for v in nums:
            x ^= v
        return x
""",
    examples=[
        {"input": {"nums": [2, 2, 1]}, "note": "1 xuất hiện một lần."},
        {"input": {"nums": [4, 1, 2, 1, 2]}, "note": "4 là số đơn."},
        {"input": {"nums": [7]}, "note": "Mảng một phần tử."},
    ],
    gen=lambda rng: [_single_case(rng) for _ in range(22)],
)


def _single_case(rng):
    k = rng.randint(0, 12)
    vals = unique_ints(rng, k + 1, -1000, 1000)
    single = vals[0]
    arr = []
    for v in vals[1:]:
        arr += [v, v]
    arr.append(single)
    rng.shuffle(arr)
    return {"nums": arr}


_p(
    title="Majority Element",
    difficulty="EASY",
    tags=["array", "hash-table", "divide-and-conquer", "sorting"],
    description=(
        "Given an array `nums` of size `n`, return the majority element — the "
        "element that appears more than `⌊n / 2⌋` times. You may assume that the "
        "majority element always exists in the array."),
    constraints="- `1 <= nums.length <= 5*10^4`\n- A majority element always exists.",
    time="O(n)", space="O(1)",
    fn="majorityElement", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def majorityElement(self, nums):
        count = 0
        cand = None
        for v in nums:
            if count == 0:
                cand = v
            count += 1 if v == cand else -1
        return cand
""",
    examples=[
        {"input": {"nums": [3, 2, 3]}, "note": "3 xuất hiện 2/3 lần."},
        {"input": {"nums": [2, 2, 1, 1, 1, 2, 2]}, "note": "2 chiếm đa số."},
        {"input": {"nums": [5]}, "note": "Một phần tử luôn là đa số."},
    ],
    gen=lambda rng: [_majority_case(rng) for _ in range(22)],
)


def _majority_case(rng):
    n = rng.randint(1, 25)
    maj = rng.randint(-50, 50)
    cnt = n // 2 + 1
    arr = [maj] * cnt
    while len(arr) < n:
        arr.append(rng.randint(-50, 50))
    rng.shuffle(arr)
    return {"nums": arr}


_p(
    title="Move Zeroes",
    difficulty="EASY",
    tags=["array", "two-pointers"],
    description=(
        "Given an integer array `nums`, move all `0`'s to the end of it while "
        "maintaining the relative order of the non-zero elements. You must do "
        "this in-place without making a copy of the array."),
    constraints="- `1 <= nums.length <= 10^4`\n- `-2^31 <= nums[i] <= 2^31 - 1`",
    time="O(n)", space="O(1)",
    fn="moveZeroes", params=[("nums", "int[]")], **{"return": "void"},
    inPlace=True, orderMatters=True,
    ref="""
class Solution:
    def moveZeroes(self, nums):
        last = 0
        for i in range(len(nums)):
            if nums[i] != 0:
                nums[last], nums[i] = nums[i], nums[last]
                last += 1
""",
    examples=[
        {"input": {"nums": [0, 1, 0, 3, 12]}, "note": "⇒ [1,3,12,0,0]."},
        {"input": {"nums": [0]}, "note": "⇒ [0]."},
        {"input": {"nums": [1, 2, 3]}, "note": "Không có số 0, giữ nguyên."},
    ],
    gen=lambda rng: [{"nums": [rng.choice([0, 0, rng.randint(-9, 9)])
                               for _ in range(rng.randint(1, 12))]}
                     for _ in range(22)],
)

_p(
    title="Squares of a Sorted Array",
    difficulty="EASY",
    tags=["array", "two-pointers", "sorting"],
    description=(
        "Given an integer array `nums` sorted in non-decreasing order, return an "
        "array of the squares of each number sorted in non-decreasing order."),
    constraints="- `1 <= nums.length <= 10^4`\n- `nums` is sorted non-decreasing.",
    time="O(n)", space="O(n)",
    fn="sortedSquares", params=[("nums", "int[]")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def sortedSquares(self, nums):
        n = len(nums)
        res = [0] * n
        l, r = 0, n - 1
        for i in range(n - 1, -1, -1):
            if abs(nums[l]) > abs(nums[r]):
                res[i] = nums[l] * nums[l]
                l += 1
            else:
                res[i] = nums[r] * nums[r]
                r -= 1
        return res
""",
    examples=[
        {"input": {"nums": [-4, -1, 0, 3, 10]}, "note": "⇒ [0,1,9,16,100]."},
        {"input": {"nums": [-7, -3, 2, 3, 11]}, "note": "⇒ [4,9,9,49,121]."},
        {"input": {"nums": [1]}, "note": "⇒ [1]."},
    ],
    gen=lambda rng: [{"nums": sorted(ints(rng, rng.randint(1, 12), -50, 50))}
                     for _ in range(22)],
)

_p(
    title="Plus One",
    difficulty="EASY",
    tags=["array", "math"],
    description=(
        "You are given a large integer represented as an integer array `digits`, "
        "where each `digits[i]` is the i-th digit of the integer (most significant "
        "first). Increment the integer by one and return the resulting array of "
        "digits."),
    constraints="- `1 <= digits.length <= 100`\n- `0 <= digits[i] <= 9`\n- No leading zeros (except the number 0).",
    time="O(n)", space="O(1)",
    fn="plusOne", params=[("digits", "int[]")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def plusOne(self, digits):
        digits = digits[:]
        for i in range(len(digits) - 1, -1, -1):
            if digits[i] < 9:
                digits[i] += 1
                return digits
            digits[i] = 0
        return [1] + digits
""",
    examples=[
        {"input": {"digits": [1, 2, 3]}, "note": "123 + 1 = 124."},
        {"input": {"digits": [9, 9]}, "note": "99 + 1 = 100."},
        {"input": {"digits": [0]}, "note": "0 + 1 = 1."},
    ],
    gen=lambda rng: ([{"digits": d} for d in [[9], [9, 9, 9], [1, 9, 9], [4, 3, 2, 1]]]
                     + [{"digits": _digits(rng)} for _ in range(18)]),
)


def _digits(rng):
    n = rng.randint(1, 8)
    d = [rng.randint(1, 9)] + [rng.randint(0, 9) for _ in range(n - 1)]
    return d


# ── dynamic programming (easy) ─────────────────────────────────────────────
_p(
    title="Climbing Stairs",
    difficulty="EASY",
    tags=["math", "dynamic-programming", "memoization"],
    description=(
        "You are climbing a staircase. It takes `n` steps to reach the top. Each "
        "time you can either climb 1 or 2 steps. In how many distinct ways can "
        "you climb to the top?"),
    constraints="- `1 <= n <= 45`",
    time="O(n)", space="O(1)",
    fn="climbStairs", params=[("n", "int")], **{"return": "int"},
    ref="""
class Solution:
    def climbStairs(self, n: int) -> int:
        a, b = 1, 1
        for _ in range(n):
            a, b = b, a + b
        return a
""",
    examples=[
        {"input": {"n": 2}, "note": "1+1 hoặc 2 ⇒ 2 cách."},
        {"input": {"n": 3}, "note": "1+1+1, 1+2, 2+1 ⇒ 3 cách."},
        {"input": {"n": 1}, "note": "1 cách."},
    ],
    gen=lambda rng: [{"n": v} for v in range(1, 23)],
)

_p(
    title="Fibonacci Number",
    difficulty="EASY",
    tags=["math", "dynamic-programming", "recursion", "memoization"],
    description=(
        "The Fibonacci numbers form a sequence such that each number is the sum of "
        "the two preceding ones, starting from 0 and 1. Given `n`, calculate "
        "`F(n)`. `F(0) = 0`, `F(1) = 1`, `F(n) = F(n-1) + F(n-2)` for `n > 1`."),
    constraints="- `0 <= n <= 40`",
    time="O(n)", space="O(1)",
    fn="fib", params=[("n", "int")], **{"return": "int"},
    ref="""
class Solution:
    def fib(self, n: int) -> int:
        a, b = 0, 1
        for _ in range(n):
            a, b = b, a + b
        return a
""",
    examples=[
        {"input": {"n": 2}, "note": "F(2)=F(1)+F(0)=1."},
        {"input": {"n": 4}, "note": "F(4)=3."},
        {"input": {"n": 0}, "note": "F(0)=0."},
    ],
    gen=lambda rng: [{"n": v} for v in range(0, 22)],
)

_p(
    title="Pascal's Triangle",
    difficulty="EASY",
    tags=["array", "dynamic-programming"],
    description=(
        "Given an integer `numRows`, return the first `numRows` rows of Pascal's "
        "triangle. Each number is the sum of the two numbers directly above it."),
    constraints="- `1 <= numRows <= 30`",
    time="O(n^2)", space="O(n^2)",
    fn="generate", params=[("numRows", "int")], **{"return": "List<List<Integer>>"},
    orderMatters=True,
    ref="""
class Solution:
    def generate(self, numRows: int):
        res = [[1]]
        for i in range(1, numRows):
            prev = res[-1]
            row = [1] + [prev[j - 1] + prev[j] for j in range(1, i)] + [1]
            res.append(row)
        return res[:numRows]
""",
    examples=[
        {"input": {"numRows": 5},
         "note": "[[1],[1,1],[1,2,1],[1,3,3,1],[1,4,6,4,1]]."},
        {"input": {"numRows": 1}, "note": "[[1]]."},
        {"input": {"numRows": 2}, "note": "[[1],[1,1]]."},
    ],
    gen=lambda rng: [{"numRows": v} for v in range(1, 23)],
)

_p(
    title="Pascal's Triangle II",
    difficulty="EASY",
    tags=["array", "dynamic-programming"],
    description=(
        "Given an integer `rowIndex`, return the `rowIndex`-th (0-indexed) row of "
        "Pascal's triangle."),
    constraints="- `0 <= rowIndex <= 33`",
    time="O(n)", space="O(n)",
    fn="getRow", params=[("rowIndex", "int")], **{"return": "List<Integer>"},
    orderMatters=True,
    ref="""
class Solution:
    def getRow(self, rowIndex: int):
        row = [1]
        for _ in range(rowIndex):
            row = [1] + [row[i] + row[i + 1] for i in range(len(row) - 1)] + [1]
        return row
""",
    examples=[
        {"input": {"rowIndex": 3}, "note": "[1,3,3,1]."},
        {"input": {"rowIndex": 0}, "note": "[1]."},
        {"input": {"rowIndex": 1}, "note": "[1,1]."},
    ],
    gen=lambda rng: [{"rowIndex": v} for v in range(0, 22)],
)

# ── trees ──────────────────────────────────────────────────────────────────
_p(
    title="Maximum Depth of Binary Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, return its maximum depth — the number "
        "of nodes along the longest path from the root node down to the farthest "
        "leaf node."),
    constraints="- The number of nodes is in the range `[0, 10^4]`.",
    time="O(n)", space="O(h)",
    fn="maxDepth", params=[("root", "TreeNode")], **{"return": "int"},
    ref="""
class Solution:
    def maxDepth(self, root):
        if not root:
            return 0
        return 1 + max(self.maxDepth(root.left), self.maxDepth(root.right))
""",
    examples=[
        {"input": {"root": [3, 9, 20, None, None, 15, 7]}, "note": "Độ sâu 3."},
        {"input": {"root": [1, None, 2]}, "note": "Độ sâu 2."},
        {"input": {"root": []}, "note": "Cây rỗng ⇒ 0."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 20), 0, 50)}
                        for _ in range(20)]),
)

_p(
    title="Invert Binary Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, invert the tree (swap every left and "
        "right child), and return its root."),
    constraints="- The number of nodes is in the range `[0, 100]`.",
    time="O(n)", space="O(h)",
    fn="invertTree", params=[("root", "TreeNode")], **{"return": "TreeNode"},
    ref="""
class Solution:
    def invertTree(self, root):
        if root:
            root.left, root.right = self.invertTree(root.right), self.invertTree(root.left)
        return root
""",
    examples=[
        {"input": {"root": [4, 2, 7, 1, 3, 6, 9]},
         "note": "⇒ [4,7,2,9,6,3,1]."},
        {"input": {"root": [2, 1, 3]}, "note": "⇒ [2,3,1]."},
        {"input": {"root": []}, "note": "Cây rỗng ⇒ []."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 18), 0, 50)}
                        for _ in range(20)]),
)

_p(
    title="Same Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the roots of two binary trees `p` and `q`, write a function to check "
        "if they are the same — structurally identical, and the nodes have the "
        "same values."),
    constraints="- Number of nodes in each tree is in `[0, 100]`.",
    time="O(n)", space="O(h)",
    fn="isSameTree", params=[("p", "TreeNode"), ("q", "TreeNode")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def isSameTree(self, p, q):
        if not p and not q:
            return True
        if not p or not q or p.val != q.val:
            return False
        return self.isSameTree(p.left, q.left) and self.isSameTree(p.right, q.right)
""",
    examples=[
        {"input": {"p": [1, 2, 3], "q": [1, 2, 3]}, "note": "Giống hệt ⇒ true."},
        {"input": {"p": [1, 2], "q": [1, None, 2]}, "note": "Khác cấu trúc ⇒ false."},
        {"input": {"p": [1, 2, 1], "q": [1, 1, 2]}, "note": "Khác giá trị ⇒ false."},
    ],
    gen=lambda rng: [_same_tree_case(rng) for _ in range(22)],
)


def _same_tree_case(rng):
    a = tree_array(rng, rng.randint(0, 12), 0, 9)
    if rng.random() < 0.5:
        return {"p": a, "q": a[:]}
    b = tree_array(rng, rng.randint(0, 12), 0, 9)
    return {"p": a, "q": b}


_p(
    title="Symmetric Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, check whether it is a mirror of itself "
        "(i.e., symmetric around its center)."),
    constraints="- The number of nodes is in the range `[1, 1000]`.",
    time="O(n)", space="O(h)",
    fn="isSymmetric", params=[("root", "TreeNode")], **{"return": "boolean"},
    ref="""
class Solution:
    def isSymmetric(self, root):
        def mir(a, b):
            if not a and not b:
                return True
            if not a or not b or a.val != b.val:
                return False
            return mir(a.left, b.right) and mir(a.right, b.left)
        return mir(root.left, root.right) if root else True
""",
    examples=[
        {"input": {"root": [1, 2, 2, 3, 4, 4, 3]}, "note": "Đối xứng ⇒ true."},
        {"input": {"root": [1, 2, 2, None, 3, None, 3]}, "note": "Không đối xứng ⇒ false."},
        {"input": {"root": [1]}, "note": "Một nút ⇒ true."},
    ],
    gen=lambda rng: [_symmetric_case(rng) for _ in range(22)],
)


def _symmetric_case(rng):
    half = random_tuple(rng, 6, 0, 5)
    root_val = rng.randint(0, 5)
    if rng.random() < 0.5:
        t = (root_val, half, mirror(half))  # symmetric
    else:
        t = (root_val, half, random_tuple(rng, 6, 0, 5))
    arr = level_serialize(t)
    if not arr:
        arr = [root_val]
    return {"root": arr}


_p(
    title="Diameter of Binary Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, return the length of the diameter of "
        "the tree — the length (number of edges) of the longest path between any "
        "two nodes, which may or may not pass through the root."),
    constraints="- The number of nodes is in the range `[1, 10^4]`.",
    time="O(n)", space="O(h)",
    fn="diameterOfBinaryTree", params=[("root", "TreeNode")], **{"return": "int"},
    ref="""
class Solution:
    def diameterOfBinaryTree(self, root):
        self.best = 0
        def h(n):
            if not n:
                return 0
            l, r = h(n.left), h(n.right)
            self.best = max(self.best, l + r)
            return 1 + max(l, r)
        h(root)
        return self.best
""",
    examples=[
        {"input": {"root": [1, 2, 3, 4, 5]}, "note": "Đường dài nhất 4-2-1-3 ⇒ 3."},
        {"input": {"root": [1, 2]}, "note": "⇒ 1."},
        {"input": {"root": [1]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: ([{"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 22), 0, 50)}
                        for _ in range(21)]),
)

_p(
    title="Balanced Binary Tree",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "binary-tree"],
    description=(
        "Given a binary tree, determine if it is height-balanced — a tree in which "
        "the left and right subtrees of every node differ in height by no more "
        "than one."),
    constraints="- The number of nodes is in the range `[0, 5000]`.",
    time="O(n)", space="O(h)",
    fn="isBalanced", params=[("root", "TreeNode")], **{"return": "boolean"},
    ref="""
class Solution:
    def isBalanced(self, root):
        def h(n):
            if not n:
                return 0
            l = h(n.left)
            if l < 0:
                return -1
            r = h(n.right)
            if r < 0 or abs(l - r) > 1:
                return -1
            return 1 + max(l, r)
        return h(root) >= 0
""",
    examples=[
        {"input": {"root": [3, 9, 20, None, None, 15, 7]}, "note": "Cân bằng ⇒ true."},
        {"input": {"root": [1, 2, 2, 3, 3, None, None, 4, 4]},
         "note": "Lệch quá 1 ⇒ false."},
        {"input": {"root": []}, "note": "Cây rỗng ⇒ true."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]},
                      {"root": [1, 2, None, 3, None, 4]}]
                     + [{"root": tree_array(rng, rng.randint(1, 20), 0, 30)}
                        for _ in range(19)]),
)

_p(
    title="Path Sum",
    difficulty="EASY",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree and an integer `targetSum`, return "
        "`true` if the tree has a root-to-leaf path such that adding up all the "
        "values along the path equals `targetSum`."),
    constraints="- The number of nodes is in the range `[0, 5000]`.\n- `-1000 <= Node.val <= 1000`",
    time="O(n)", space="O(h)",
    fn="hasPathSum", params=[("root", "TreeNode"), ("targetSum", "int")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def hasPathSum(self, root, targetSum):
        if not root:
            return False
        if not root.left and not root.right:
            return root.val == targetSum
        rem = targetSum - root.val
        return self.hasPathSum(root.left, rem) or self.hasPathSum(root.right, rem)
""",
    examples=[
        {"input": {"root": [5, 4, 8, 11, None, 13, 4, 7, 2, None, None, None, 1],
                   "targetSum": 22}, "note": "5→4→11→2 = 22 ⇒ true."},
        {"input": {"root": [1, 2, 3], "targetSum": 5}, "note": "Không path nào =5 ⇒ false."},
        {"input": {"root": [], "targetSum": 0}, "note": "Cây rỗng ⇒ false."},
    ],
    gen=lambda rng: [_pathsum_case(rng) for _ in range(22)],
)


def _pathsum_case(rng):
    arr = tree_array(rng, rng.randint(1, 14), -5, 5)
    if not arr:
        arr = [rng.randint(-5, 5)]
    return {"root": arr, "targetSum": rng.randint(-8, 18)}


_p(
    title="Binary Tree Inorder Traversal",
    difficulty="EASY",
    tags=["stack", "tree", "depth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, return the inorder traversal of its "
        "nodes' values."),
    constraints="- The number of nodes is in the range `[0, 100]`.",
    time="O(n)", space="O(h)",
    fn="inorderTraversal", params=[("root", "TreeNode")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def inorderTraversal(self, root):
        res, stack, cur = [], [], root
        while cur or stack:
            while cur:
                stack.append(cur)
                cur = cur.left
            cur = stack.pop()
            res.append(cur.val)
            cur = cur.right
        return res
""",
    examples=[
        {"input": {"root": [1, None, 2, 3]}, "note": "⇒ [1,3,2]."},
        {"input": {"root": []}, "note": "⇒ []."},
        {"input": {"root": [1]}, "note": "⇒ [1]."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 18), 0, 99)}
                        for _ in range(20)]),
)

# ── linked lists ───────────────────────────────────────────────────────────
_p(
    title="Reverse Linked List",
    difficulty="EASY",
    tags=["linked-list", "recursion"],
    description=(
        "Given the `head` of a singly linked list, reverse the list, and return "
        "the reversed list."),
    constraints="- The number of nodes is in the range `[0, 5000]`.",
    time="O(n)", space="O(1)",
    fn="reverseList", params=[("head", "ListNode")], **{"return": "ListNode"},
    ref="""
class Solution:
    def reverseList(self, head):
        prev = None
        while head:
            head.next, prev, head = prev, head, head.next
        return prev
""",
    examples=[
        {"input": {"head": [1, 2, 3, 4, 5]}, "note": "⇒ [5,4,3,2,1]."},
        {"input": {"head": [1, 2]}, "note": "⇒ [2,1]."},
        {"input": {"head": []}, "note": "Danh sách rỗng ⇒ []."},
    ],
    gen=lambda rng: ([{"head": []}, {"head": [1]}]
                     + [{"head": ints(rng, rng.randint(1, 12), -50, 50)}
                        for _ in range(20)]),
)

_p(
    title="Merge Two Sorted Lists",
    difficulty="EASY",
    tags=["linked-list", "recursion"],
    description=(
        "You are given the heads of two sorted linked lists `list1` and `list2`. "
        "Merge the two lists into one sorted list and return its head."),
    constraints="- Number of nodes in both lists is in `[0, 50]`.\n- Both lists are sorted non-decreasing.",
    time="O(n + m)", space="O(1)",
    fn="mergeTwoLists", params=[("list1", "ListNode"), ("list2", "ListNode")],
    **{"return": "ListNode"},
    ref="""
class Solution:
    def mergeTwoLists(self, list1, list2):
        dummy = ListNode(0)
        tail = dummy
        while list1 and list2:
            if list1.val <= list2.val:
                tail.next, list1 = list1, list1.next
            else:
                tail.next, list2 = list2, list2.next
            tail = tail.next
        tail.next = list1 or list2
        return dummy.next
""",
    examples=[
        {"input": {"list1": [1, 2, 4], "list2": [1, 3, 4]}, "note": "⇒ [1,1,2,3,4,4]."},
        {"input": {"list1": [], "list2": []}, "note": "⇒ []."},
        {"input": {"list1": [], "list2": [0]}, "note": "⇒ [0]."},
    ],
    gen=lambda rng: [{"list1": sorted(ints(rng, rng.randint(0, 8), -20, 20)),
                      "list2": sorted(ints(rng, rng.randint(0, 8), -20, 20))}
                     for _ in range(22)],
)

_p(
    title="Middle of the Linked List",
    difficulty="EASY",
    tags=["linked-list", "two-pointers"],
    description=(
        "Given the `head` of a singly linked list, return the middle node. If "
        "there are two middle nodes, return the second middle node."),
    constraints="- The number of nodes is in the range `[1, 100]`.",
    time="O(n)", space="O(1)",
    fn="middleNode", params=[("head", "ListNode")], **{"return": "ListNode"},
    ref="""
class Solution:
    def middleNode(self, head):
        slow = fast = head
        while fast and fast.next:
            slow = slow.next
            fast = fast.next.next
        return slow
""",
    examples=[
        {"input": {"head": [1, 2, 3, 4, 5]}, "note": "Giữa là 3 ⇒ [3,4,5]."},
        {"input": {"head": [1, 2, 3, 4, 5, 6]}, "note": "Hai giữa, lấy thứ hai ⇒ [4,5,6]."},
        {"input": {"head": [1]}, "note": "⇒ [1]."},
    ],
    gen=lambda rng: [{"head": ints(rng, rng.randint(1, 12), -30, 30)}
                     for _ in range(22)],
)

_p(
    title="Palindrome Linked List",
    difficulty="EASY",
    tags=["linked-list", "two-pointers", "stack", "recursion"],
    description=(
        "Given the `head` of a singly linked list, return `true` if it is a "
        "palindrome or `false` otherwise."),
    constraints="- The number of nodes is in the range `[1, 10^5]`.",
    time="O(n)", space="O(1)",
    fn="isPalindrome", params=[("head", "ListNode")], **{"return": "boolean"},
    ref="""
class Solution:
    def isPalindrome(self, head):
        vals = []
        while head:
            vals.append(head.val)
            head = head.next
        return vals == vals[::-1]
""",
    examples=[
        {"input": {"head": [1, 2, 2, 1]}, "note": "Đối xứng ⇒ true."},
        {"input": {"head": [1, 2]}, "note": "⇒ false."},
        {"input": {"head": [1]}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_palindrome_list_case(rng) for _ in range(22)],
)


def _palindrome_list_case(rng):
    n = rng.randint(1, 8)
    half = ints(rng, n, 0, 3)
    if rng.random() < 0.5:
        full = half + (half[::-1] if rng.random() < 0.5 else half[-2::-1])
        return {"head": full}
    return {"head": ints(rng, rng.randint(1, 10), 0, 3)}


_p(
    title="Remove Duplicates from Sorted List",
    difficulty="EASY",
    tags=["linked-list"],
    description=(
        "Given the `head` of a sorted linked list, delete all duplicates such that "
        "each element appears only once. Return the linked list sorted as well."),
    constraints="- The number of nodes is in the range `[0, 300]`.\n- The list is sorted non-decreasing.",
    time="O(n)", space="O(1)",
    fn="deleteDuplicates", params=[("head", "ListNode")], **{"return": "ListNode"},
    ref="""
class Solution:
    def deleteDuplicates(self, head):
        cur = head
        while cur and cur.next:
            if cur.next.val == cur.val:
                cur.next = cur.next.next
            else:
                cur = cur.next
        return head
""",
    examples=[
        {"input": {"head": [1, 1, 2]}, "note": "⇒ [1,2]."},
        {"input": {"head": [1, 1, 2, 3, 3]}, "note": "⇒ [1,2,3]."},
        {"input": {"head": []}, "note": "⇒ []."},
    ],
    gen=lambda rng: ([{"head": []}, {"head": [1]}]
                     + [{"head": sorted(ints(rng, rng.randint(1, 12), 0, 6))}
                        for _ in range(20)]),
)

# ── strings ────────────────────────────────────────────────────────────────
_p(
    title="Is Subsequence",
    difficulty="EASY",
    tags=["two-pointers", "string", "dynamic-programming"],
    description=(
        "Given two strings `s` and `t`, return `true` if `s` is a subsequence of "
        "`t`, or `false` otherwise. A subsequence is formed by deleting some (or "
        "no) characters without changing the relative order of the remaining "
        "characters."),
    constraints="- `0 <= s.length <= 100`\n- `0 <= t.length <= 10^4`\n- Lowercase English letters.",
    time="O(n)", space="O(1)",
    fn="isSubsequence", params=[("s", "string"), ("t", "string")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def isSubsequence(self, s, t):
        it = iter(t)
        return all(c in it for c in s)
""",
    examples=[
        {"input": {"s": "abc", "t": "ahbgdc"}, "note": "a,b,c xuất hiện đúng thứ tự ⇒ true."},
        {"input": {"s": "axc", "t": "ahbgdc"}, "note": "Không có x đúng thứ tự ⇒ false."},
        {"input": {"s": "", "t": "abc"}, "note": "Chuỗi rỗng luôn là subsequence ⇒ true."},
    ],
    gen=lambda rng: [{"s": lower(rng, rng.randint(0, 5)),
                      "t": lower(rng, rng.randint(0, 12))} for _ in range(22)],
)

_p(
    title="Longest Common Prefix",
    difficulty="EASY",
    tags=["string", "trie"],
    description=(
        "Write a function to find the longest common prefix string amongst an "
        "array of strings. If there is no common prefix, return an empty string."),
    constraints="- `1 <= strs.length <= 200`\n- `0 <= strs[i].length <= 200`\n- Lowercase English letters.",
    time="O(S)", space="O(1)",
    fn="longestCommonPrefix", params=[("strs", "string[]")], **{"return": "string"},
    ref="""
class Solution:
    def longestCommonPrefix(self, strs):
        if not strs:
            return ""
        pre = strs[0]
        for s in strs[1:]:
            while not s.startswith(pre):
                pre = pre[:-1]
                if not pre:
                    return ""
        return pre
""",
    examples=[
        {"input": {"strs": ["flower", "flow", "flight"]}, "note": "⇒ \"fl\"."},
        {"input": {"strs": ["dog", "racecar", "car"]}, "note": "Không có tiền tố chung ⇒ \"\"."},
        {"input": {"strs": ["abc"]}, "note": "⇒ \"abc\"."},
    ],
    gen=lambda rng: [_lcp_case(rng) for _ in range(22)],
)


def _lcp_case(rng):
    base = lower(rng, rng.randint(1, 4))
    out = []
    for _ in range(rng.randint(1, 5)):
        if rng.random() < 0.6:
            out.append(base + lower(rng, rng.randint(0, 4)))
        else:
            out.append(lower(rng, rng.randint(1, 6)))
    return {"strs": out}


_p(
    title="Merge Strings Alternately",
    difficulty="EASY",
    tags=["two-pointers", "string"],
    description=(
        "You are given two strings `word1` and `word2`. Merge them by adding "
        "letters in alternating order, starting with `word1`. If one string is "
        "longer, append the additional letters onto the end of the merged string."),
    constraints="- `1 <= word1.length, word2.length <= 100`\n- Lowercase English letters.",
    time="O(n + m)", space="O(n + m)",
    fn="mergeAlternately", params=[("word1", "string"), ("word2", "string")],
    **{"return": "string"},
    ref="""
class Solution:
    def mergeAlternately(self, word1, word2):
        res = []
        i = 0
        while i < len(word1) or i < len(word2):
            if i < len(word1):
                res.append(word1[i])
            if i < len(word2):
                res.append(word2[i])
            i += 1
        return "".join(res)
""",
    examples=[
        {"input": {"word1": "abc", "word2": "pqr"}, "note": "⇒ \"apbqcr\"."},
        {"input": {"word1": "ab", "word2": "pqrs"}, "note": "⇒ \"apbqrs\"."},
        {"input": {"word1": "abcd", "word2": "pq"}, "note": "⇒ \"apbqcd\"."},
    ],
    gen=lambda rng: [{"word1": lower(rng, rng.randint(1, 8)),
                      "word2": lower(rng, rng.randint(1, 8))} for _ in range(22)],
)

_p(
    title="Find the Index of the First Occurrence in a String",
    difficulty="EASY",
    tags=["two-pointers", "string", "string-matching"],
    description=(
        "Given two strings `haystack` and `needle`, return the index of the first "
        "occurrence of `needle` in `haystack`, or `-1` if `needle` is not part of "
        "`haystack`."),
    constraints="- `1 <= haystack.length, needle.length <= 10^4`\n- Lowercase English letters.",
    time="O(n*m)", space="O(1)",
    fn="strStr", params=[("haystack", "string"), ("needle", "string")],
    **{"return": "int"},
    ref="""
class Solution:
    def strStr(self, haystack, needle):
        return haystack.find(needle)
""",
    examples=[
        {"input": {"haystack": "sadbutsad", "needle": "sad"}, "note": "Vị trí đầu là 0."},
        {"input": {"haystack": "leetcode", "needle": "leeto"}, "note": "Không tồn tại ⇒ -1."},
        {"input": {"haystack": "abc", "needle": "c"}, "note": "Vị trí 2."},
    ],
    gen=lambda rng: [_strstr_case(rng) for _ in range(22)],
)


def _strstr_case(rng):
    hay = lower(rng, rng.randint(3, 12))
    if rng.random() < 0.6 and len(hay) >= 2:
        i = rng.randint(0, len(hay) - 2)
        needle = hay[i:i + rng.randint(1, len(hay) - i)]
    else:
        needle = lower(rng, rng.randint(1, 4))
    return {"haystack": hay, "needle": needle}


_p(
    title="First Unique Character in a String",
    difficulty="EASY",
    tags=["hash-table", "string", "queue", "counting"],
    description=(
        "Given a string `s`, find the first non-repeating character in it and "
        "return its index. If it does not exist, return `-1`."),
    constraints="- `1 <= s.length <= 10^5`\n- `s` consists of lowercase English letters.",
    time="O(n)", space="O(1)",
    fn="firstUniqChar", params=[("s", "string")], **{"return": "int"},
    ref="""
class Solution:
    def firstUniqChar(self, s):
        from collections import Counter
        cnt = Counter(s)
        for i, c in enumerate(s):
            if cnt[c] == 1:
                return i
        return -1
""",
    examples=[
        {"input": {"s": "leetcode"}, "note": "'l' đầu tiên không lặp ⇒ 0."},
        {"input": {"s": "loveleetcode"}, "note": "'v' tại vị trí 2."},
        {"input": {"s": "aabb"}, "note": "Mọi ký tự đều lặp ⇒ -1."},
    ],
    gen=lambda rng: [{"s": lower(rng, rng.randint(1, 14))} for _ in range(22)],
)

_p(
    title="Ransom Note",
    difficulty="EASY",
    tags=["hash-table", "string", "counting"],
    description=(
        "Given two strings `ransomNote` and `magazine`, return `true` if "
        "`ransomNote` can be constructed by using the letters from `magazine` and "
        "`false` otherwise. Each letter in `magazine` can only be used once."),
    constraints="- `1 <= ransomNote.length, magazine.length <= 10^5`\n- Lowercase English letters.",
    time="O(n)", space="O(1)",
    fn="canConstruct", params=[("ransomNote", "string"), ("magazine", "string")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def canConstruct(self, ransomNote, magazine):
        from collections import Counter
        need = Counter(ransomNote)
        have = Counter(magazine)
        return all(have[c] >= n for c, n in need.items())
""",
    examples=[
        {"input": {"ransomNote": "a", "magazine": "b"}, "note": "Không đủ 'a' ⇒ false."},
        {"input": {"ransomNote": "aa", "magazine": "ab"}, "note": "Chỉ một 'a' ⇒ false."},
        {"input": {"ransomNote": "aa", "magazine": "aab"}, "note": "Đủ ⇒ true."},
    ],
    gen=lambda rng: [{"ransomNote": lower(rng, rng.randint(1, 6)),
                      "magazine": lower(rng, rng.randint(1, 12))} for _ in range(22)],
)

# ── prefix sums ────────────────────────────────────────────────────────────
_p(
    title="Running Sum of 1d Array",
    difficulty="EASY",
    tags=["array", "prefix-sum"],
    description=(
        "Given an array `nums`, return the running sum where "
        "`runningSum[i] = sum(nums[0..i])`."),
    constraints="- `1 <= nums.length <= 1000`\n- `-10^6 <= nums[i] <= 10^6`",
    time="O(n)", space="O(1)",
    fn="runningSum", params=[("nums", "int[]")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def runningSum(self, nums):
        out = []
        s = 0
        for v in nums:
            s += v
            out.append(s)
        return out
""",
    examples=[
        {"input": {"nums": [1, 2, 3, 4]}, "note": "⇒ [1,3,6,10]."},
        {"input": {"nums": [1, 1, 1, 1, 1]}, "note": "⇒ [1,2,3,4,5]."},
        {"input": {"nums": [3, -2, 5]}, "note": "⇒ [3,1,6]."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 12), -20, 20)}
                     for _ in range(22)],
)

_p(
    title="Find Pivot Index",
    difficulty="EASY",
    tags=["array", "prefix-sum"],
    description=(
        "Given an array of integers `nums`, calculate the pivot index — the index "
        "where the sum of all numbers strictly to the left equals the sum of all "
        "numbers strictly to the right. Return the leftmost pivot index, or `-1` "
        "if none exists."),
    constraints="- `1 <= nums.length <= 10^4`\n- `-1000 <= nums[i] <= 1000`",
    time="O(n)", space="O(1)",
    fn="pivotIndex", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def pivotIndex(self, nums):
        total = sum(nums)
        left = 0
        for i, v in enumerate(nums):
            if left == total - left - v:
                return i
            left += v
        return -1
""",
    examples=[
        {"input": {"nums": [1, 7, 3, 6, 5, 6]}, "note": "Tại i=3: 1+7+3 = 5+6 ⇒ 3."},
        {"input": {"nums": [1, 2, 3]}, "note": "Không có pivot ⇒ -1."},
        {"input": {"nums": [2, 1, -1]}, "note": "Tại i=0: trái=0, phải=0 ⇒ 0."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 10), -6, 6)}
                     for _ in range(22)],
)

_p(
    title="How Many Numbers Are Smaller Than the Current Number",
    difficulty="EASY",
    tags=["array", "hash-table", "counting", "sorting"],
    description=(
        "Given the array `nums`, for each `nums[i]` find out how many numbers in "
        "the array are smaller than it. Return the answer as an array where "
        "`answer[i]` is the count for `nums[i]`."),
    constraints="- `2 <= nums.length <= 500`\n- `0 <= nums[i] <= 100`",
    time="O(n log n)", space="O(n)",
    fn="smallerNumbersThanCurrent", params=[("nums", "int[]")], **{"return": "int[]"},
    orderMatters=True,
    ref="""
class Solution:
    def smallerNumbersThanCurrent(self, nums):
        s = sorted(nums)
        first = {}
        for i, v in enumerate(s):
            if v not in first:
                first[v] = i
        return [first[v] for v in nums]
""",
    examples=[
        {"input": {"nums": [8, 1, 2, 2, 3]}, "note": "⇒ [4,0,1,1,3]."},
        {"input": {"nums": [6, 5, 4, 8]}, "note": "⇒ [2,1,0,3]."},
        {"input": {"nums": [7, 7, 7, 7]}, "note": "⇒ [0,0,0,0]."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(2, 12), 0, 12)}
                     for _ in range(22)],
)

# ── stack / heap ───────────────────────────────────────────────────────────
_p(
    title="Last Stone Weight",
    difficulty="EASY",
    tags=["array", "heap", "greedy"],
    description=(
        "You are given an array of stones' weights. Each turn, smash the two "
        "heaviest stones together: if equal, both are destroyed; otherwise the "
        "lighter is destroyed and the heavier becomes the difference. Return the "
        "weight of the last remaining stone, or `0` if none remain."),
    constraints="- `1 <= stones.length <= 30`\n- `1 <= stones[i] <= 1000`",
    time="O(n log n)", space="O(n)",
    fn="lastStoneWeight", params=[("stones", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def lastStoneWeight(self, stones):
        import heapq
        h = [-s for s in stones]
        heapq.heapify(h)
        while len(h) > 1:
            a = -heapq.heappop(h)
            b = -heapq.heappop(h)
            if a != b:
                heapq.heappush(h, -(a - b))
        return -h[0] if h else 0
""",
    examples=[
        {"input": {"stones": [2, 7, 4, 1, 8, 1]}, "note": "Còn lại 1."},
        {"input": {"stones": [1]}, "note": "Còn 1."},
        {"input": {"stones": [2, 2]}, "note": "Triệt tiêu hết ⇒ 0."},
    ],
    gen=lambda rng: [{"stones": ints(rng, rng.randint(1, 12), 1, 20)}
                     for _ in range(22)],
)

# ── parsing ────────────────────────────────────────────────────────────────
_p(
    title="Roman to Integer",
    difficulty="EASY",
    tags=["hash-table", "math", "string"],
    description=(
        "Given a roman numeral, convert it to an integer. Roman numerals use the "
        "symbols I, V, X, L, C, D, M with the subtractive rule (e.g. IV = 4, "
        "IX = 9, XL = 40)."),
    constraints="- `1 <= s.length <= 15`\n- `s` is a valid roman numeral in `[1, 3999]`.",
    time="O(n)", space="O(1)",
    fn="romanToInt", params=[("s", "string")], **{"return": "int"},
    ref="""
class Solution:
    def romanToInt(self, s):
        m = {"I":1,"V":5,"X":10,"L":50,"C":100,"D":500,"M":1000}
        total = 0
        for i, c in enumerate(s):
            if i + 1 < len(s) and m[c] < m[s[i + 1]]:
                total -= m[c]
            else:
                total += m[c]
        return total
""",
    examples=[
        {"input": {"s": "III"}, "note": "1+1+1 = 3."},
        {"input": {"s": "LVIII"}, "note": "50+5+3 = 58."},
        {"input": {"s": "MCMXCIV"}, "note": "1000+900+90+4 = 1994."},
    ],
    gen=lambda rng: [{"s": _to_roman(rng.randint(1, 3999))} for _ in range(22)],
)


def _to_roman(num):
    vals = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"),
            (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"),
            (5, "V"), (4, "IV"), (1, "I")]
    out = []
    for v, sym in vals:
        while num >= v:
            out.append(sym)
            num -= v
    return "".join(out)


_p(
    title="Length of Last Word",
    difficulty="EASY",
    tags=["string"],
    description=(
        "Given a string `s` consisting of words and spaces, return the length of "
        "the last word. A word is a maximal substring consisting of non-space "
        "characters only."),
    constraints="- `1 <= s.length <= 10^4`\n- `s` consists of English letters and spaces.",
    time="O(n)", space="O(1)",
    fn="lengthOfLastWord", params=[("s", "string")], **{"return": "int"},
    ref="""
class Solution:
    def lengthOfLastWord(self, s):
        parts = s.split()
        return len(parts[-1]) if parts else 0
""",
    examples=[
        {"input": {"s": "Hello World"}, "note": "Từ cuối \"World\" dài 5."},
        {"input": {"s": "   fly me   to   the moon  "}, "note": "Từ cuối \"moon\" dài 4."},
        {"input": {"s": "a"}, "note": "Dài 1."},
    ],
    gen=lambda rng: [{"s": _spacey_words(rng)} for _ in range(22)],
)


def _spacey_words(rng):
    n = rng.randint(1, 4)
    parts = []
    for _ in range(n):
        parts.append(" " * rng.randint(0, 2) + lower(rng, rng.randint(1, 5)))
    return (" ".join(parts) + " " * rng.randint(0, 3)) or "a"
