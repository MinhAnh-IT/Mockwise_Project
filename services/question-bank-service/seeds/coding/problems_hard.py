"""HARD problems — classic interview hards, deterministic, supported types only."""
from __future__ import annotations

from helpers import ints, unique_ints, lower, tree_array

PROBLEMS = []


def _p(**kw):
    PROBLEMS.append(kw)


def grid01(rng, r, c, p1=0.6):
    return [["1" if rng.random() < p1 else "0" for _ in range(c)] for _ in range(r)]


def matrix(rng, r, c, lo, hi):
    return [[rng.randint(lo, hi) for _ in range(c)] for _ in range(r)]


# ── arrays / stack ─────────────────────────────────────────────────────────
_p(
    title="Trapping Rain Water",
    difficulty="HARD",
    tags=["array", "two-pointers", "dynamic-programming", "stack", "monotonic-stack"],
    description=(
        "Given `n` non-negative integers representing an elevation map where the "
        "width of each bar is 1, compute how much water it can trap after raining."),
    constraints="- `n == height.length`\n- `1 <= n <= 2*10^4`\n- `0 <= height[i] <= 10^5`",
    time="O(n)", space="O(1)",
    fn="trap", params=[("height", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def trap(self, height):
        l, r = 0, len(height) - 1
        lm = rm = res = 0
        while l < r:
            if height[l] < height[r]:
                lm = max(lm, height[l]); res += lm - height[l]; l += 1
            else:
                rm = max(rm, height[r]); res += rm - height[r]; r -= 1
        return res
""",
    examples=[
        {"input": {"height": [0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1]}, "note": "⇒ 6."},
        {"input": {"height": [4, 2, 0, 3, 2, 5]}, "note": "⇒ 9."},
        {"input": {"height": [1]}, "note": "Không giữ nước ⇒ 0."},
    ],
    gen=lambda rng: [{"height": ints(rng, rng.randint(1, 14), 0, 8)} for _ in range(24)],
)

_p(
    title="Largest Rectangle in Histogram",
    difficulty="HARD",
    tags=["array", "stack", "monotonic-stack"],
    description=(
        "Given an array of integers `heights` representing the histogram's bar "
        "heights where the width of each bar is 1, return the area of the largest "
        "rectangle in the histogram."),
    constraints="- `1 <= heights.length <= 10^5`\n- `0 <= heights[i] <= 10^4`",
    time="O(n)", space="O(n)",
    fn="largestRectangleArea", params=[("heights", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def largestRectangleArea(self, heights):
        stack = []
        best = 0
        for i, h in enumerate(heights + [0]):
            start = i
            while stack and stack[-1][1] > h:
                idx, height = stack.pop()
                best = max(best, height * (i - idx))
                start = idx
            stack.append((start, h))
        return best
""",
    examples=[
        {"input": {"heights": [2, 1, 5, 6, 2, 3]}, "note": "5&6 ⇒ 10."},
        {"input": {"heights": [2, 4]}, "note": "⇒ 4."},
        {"input": {"heights": [0]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"heights": ints(rng, rng.randint(1, 14), 0, 9)} for _ in range(24)],
)

_p(
    title="Maximal Rectangle",
    difficulty="HARD",
    tags=["array", "dynamic-programming", "stack", "matrix", "monotonic-stack"],
    description=(
        "Given a `rows x cols` binary `matrix` filled with 0's and 1's, find the "
        "largest rectangle containing only 1's and return its area."),
    constraints="- `1 <= rows, cols <= 200`\n- `matrix[i][j]` is '0' or '1'.",
    time="O(rows*cols)", space="O(cols)",
    fn="maximalRectangle", params=[("matrix", "char[][]")], **{"return": "int"},
    ref="""
class Solution:
    def maximalRectangle(self, matrix):
        if not matrix:
            return 0
        n = len(matrix[0])
        heights = [0] * n
        best = 0
        for row in matrix:
            for j in range(n):
                heights[j] = heights[j] + 1 if row[j] == "1" else 0
            stack = []
            for i, h in enumerate(heights + [0]):
                start = i
                while stack and stack[-1][1] > h:
                    idx, height = stack.pop()
                    best = max(best, height * (i - idx))
                    start = idx
                stack.append((start, h))
        return best
""",
    examples=[
        {"input": {"matrix": [["1", "0", "1", "0", "0"], ["1", "0", "1", "1", "1"],
                              ["1", "1", "1", "1", "1"], ["1", "0", "0", "1", "0"]]},
         "note": "⇒ 6."},
        {"input": {"matrix": [["0", "1"], ["1", "1"]]}, "note": "⇒ 2."},
        {"input": {"matrix": [["0"]]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"matrix": grid01(rng, rng.randint(1, 5), rng.randint(1, 5))}
                     for _ in range(24)],
)

_p(
    title="Sliding Window Maximum",
    difficulty="HARD",
    tags=["array", "queue", "sliding-window", "heap", "monotonic-queue"],
    description=(
        "You are given an array of integers `nums` and a window size `k`. The "
        "window moves from the very left to the very right, one position at a "
        "time. Return the maximum of each window."),
    constraints="- `1 <= k <= nums.length <= 10^5`\n- `-10^4 <= nums[i] <= 10^4`",
    time="O(n)", space="O(k)",
    fn="maxSlidingWindow", params=[("nums", "int[]"), ("k", "int")],
    **{"return": "int[]"}, orderMatters=True,
    ref="""
class Solution:
    def maxSlidingWindow(self, nums, k):
        from collections import deque
        dq = deque()
        res = []
        for i, v in enumerate(nums):
            while dq and nums[dq[-1]] <= v:
                dq.pop()
            dq.append(i)
            if dq[0] <= i - k:
                dq.popleft()
            if i >= k - 1:
                res.append(nums[dq[0]])
        return res
""",
    examples=[
        {"input": {"nums": [1, 3, -1, -3, 5, 3, 6, 7], "k": 3},
         "note": "⇒ [3,3,5,5,6,7]."},
        {"input": {"nums": [1], "k": 1}, "note": "⇒ [1]."},
        {"input": {"nums": [9, 11], "k": 2}, "note": "⇒ [11]."},
    ],
    gen=lambda rng: [_window_case(rng) for _ in range(24)],
)


def _window_case(rng):
    n = rng.randint(1, 14)
    return {"nums": ints(rng, n, -9, 9), "k": rng.randint(1, n)}


_p(
    title="First Missing Positive",
    difficulty="HARD",
    tags=["array", "hash-table"],
    description=(
        "Given an unsorted integer array `nums`, return the smallest missing "
        "positive integer. You must implement an algorithm that runs in O(n) time "
        "and uses O(1) auxiliary space."),
    constraints="- `1 <= nums.length <= 10^5`\n- `-2^31 <= nums[i] <= 2^31 - 1`",
    time="O(n)", space="O(1)",
    fn="firstMissingPositive", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def firstMissingPositive(self, nums):
        s = set(nums)
        i = 1
        while i in s:
            i += 1
        return i
""",
    examples=[
        {"input": {"nums": [1, 2, 0]}, "note": "⇒ 3."},
        {"input": {"nums": [3, 4, -1, 1]}, "note": "⇒ 2."},
        {"input": {"nums": [7, 8, 9, 11, 12]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 12), -5, 12)} for _ in range(24)],
)

_p(
    title="Candy",
    difficulty="HARD",
    tags=["array", "greedy"],
    description=(
        "There are `n` children standing in a line, each with a rating value in "
        "`ratings`. You give candies subject to: each child gets at least one "
        "candy, and a child with a higher rating than an adjacent child gets more "
        "candies than that neighbor. Return the minimum number of candies."),
    constraints="- `n == ratings.length`\n- `1 <= n <= 2*10^4`\n- `0 <= ratings[i] <= 2*10^4`",
    time="O(n)", space="O(n)",
    fn="candy", params=[("ratings", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def candy(self, ratings):
        n = len(ratings)
        c = [1] * n
        for i in range(1, n):
            if ratings[i] > ratings[i - 1]:
                c[i] = c[i - 1] + 1
        for i in range(n - 2, -1, -1):
            if ratings[i] > ratings[i + 1]:
                c[i] = max(c[i], c[i + 1] + 1)
        return sum(c)
""",
    examples=[
        {"input": {"ratings": [1, 0, 2]}, "note": "⇒ 5."},
        {"input": {"ratings": [1, 2, 2]}, "note": "⇒ 4."},
        {"input": {"ratings": [5]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [{"ratings": ints(rng, rng.randint(1, 12), 0, 5)} for _ in range(24)],
)

_p(
    title="Jump Game II",
    difficulty="HARD",
    tags=["array", "dynamic-programming", "greedy"],
    description=(
        "You are given a 0-indexed array `nums`. You start at index 0. "
        "`nums[i]` is the maximum forward jump length from index `i`. Return the "
        "minimum number of jumps to reach the last index. Test cases are generated "
        "so that you can always reach the last index."),
    constraints="- `1 <= nums.length <= 10^4`\n- `0 <= nums[i] <= 1000`\n- Last index is always reachable.",
    time="O(n)", space="O(1)",
    fn="jump", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def jump(self, nums):
        jumps = end = farthest = 0
        for i in range(len(nums) - 1):
            farthest = max(farthest, i + nums[i])
            if i == end:
                jumps += 1
                end = farthest
        return jumps
""",
    examples=[
        {"input": {"nums": [2, 3, 1, 1, 4]}, "note": "⇒ 2."},
        {"input": {"nums": [2, 3, 0, 1, 4]}, "note": "⇒ 2."},
        {"input": {"nums": [0]}, "note": "Đã ở cuối ⇒ 0."},
    ],
    gen=lambda rng: [{"nums": _reachable_jumps(rng)} for _ in range(24)],
)


def _reachable_jumps(rng):
    n = rng.randint(1, 12)
    nums = [0] * n
    for i in range(n - 1):
        # guarantee reachability: at least 1 step forward available
        nums[i] = rng.randint(1, max(1, n - 1 - i))
    return nums


# ── dynamic programming (hard) ─────────────────────────────────────────────
_p(
    title="Edit Distance",
    difficulty="HARD",
    tags=["string", "dynamic-programming"],
    description=(
        "Given two strings `word1` and `word2`, return the minimum number of "
        "operations (insert, delete, or replace a character) required to convert "
        "`word1` to `word2`."),
    constraints="- `0 <= word1.length, word2.length <= 500`\n- Lowercase English letters.",
    time="O(n*m)", space="O(m)",
    fn="minDistance", params=[("word1", "string"), ("word2", "string")],
    **{"return": "int"},
    ref="""
class Solution:
    def minDistance(self, word1, word2):
        m, n = len(word1), len(word2)
        dp = list(range(n + 1))
        for i in range(1, m + 1):
            prev = dp[0]
            dp[0] = i
            for j in range(1, n + 1):
                cur = dp[j]
                if word1[i - 1] == word2[j - 1]:
                    dp[j] = prev
                else:
                    dp[j] = 1 + min(prev, dp[j], dp[j - 1])
                prev = cur
        return dp[n]
""",
    examples=[
        {"input": {"word1": "horse", "word2": "ros"}, "note": "⇒ 3."},
        {"input": {"word1": "intention", "word2": "execution"}, "note": "⇒ 5."},
        {"input": {"word1": "", "word2": "abc"}, "note": "⇒ 3."},
    ],
    gen=lambda rng: [{"word1": lower(rng, rng.randint(0, 6)),
                      "word2": lower(rng, rng.randint(0, 6))} for _ in range(24)],
)

_p(
    title="Distinct Subsequences",
    difficulty="HARD",
    tags=["string", "dynamic-programming"],
    description=(
        "Given two strings `s` and `t`, return the number of distinct subsequences "
        "of `s` which equal `t`. A subsequence keeps relative order but may skip "
        "characters. The answer fits in a 32-bit signed integer."),
    constraints="- `1 <= s.length, t.length <= 1000`\n- Lowercase English letters.",
    time="O(n*m)", space="O(m)",
    fn="numDistinct", params=[("s", "string"), ("t", "string")], **{"return": "int"},
    ref="""
class Solution:
    def numDistinct(self, s, t):
        n = len(t)
        dp = [1] + [0] * n
        for c in s:
            for j in range(n, 0, -1):
                if c == t[j - 1]:
                    dp[j] += dp[j - 1]
        return dp[n]
""",
    examples=[
        {"input": {"s": "rabbbit", "t": "rabbit"}, "note": "⇒ 3."},
        {"input": {"s": "babgbag", "t": "bag"}, "note": "⇒ 5."},
        {"input": {"s": "a", "t": "b"}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"s": lower(rng, rng.randint(1, 7)),
                      "t": lower(rng, rng.randint(1, 4))} for _ in range(24)],
)

_p(
    title="Longest Valid Parentheses",
    difficulty="HARD",
    tags=["string", "dynamic-programming", "stack"],
    description=(
        "Given a string containing just the characters '(' and ')', return the "
        "length of the longest valid (well-formed) parentheses substring."),
    constraints="- `0 <= s.length <= 3*10^4`\n- `s[i]` is '(' or ')'.",
    time="O(n)", space="O(n)",
    fn="longestValidParentheses", params=[("s", "string")], **{"return": "int"},
    ref="""
class Solution:
    def longestValidParentheses(self, s):
        stack = [-1]
        best = 0
        for i, c in enumerate(s):
            if c == "(":
                stack.append(i)
            else:
                stack.pop()
                if not stack:
                    stack.append(i)
                else:
                    best = max(best, i - stack[-1])
        return best
""",
    examples=[
        {"input": {"s": "(()"}, "note": "\"()\" ⇒ 2."},
        {"input": {"s": ")()())"}, "note": "\"()()\" ⇒ 4."},
        {"input": {"s": ""}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"s": "".join(rng.choice("()") for _ in range(rng.randint(0, 12)))}
                     for _ in range(24)],
)

_p(
    title="Best Time to Buy and Sell Stock III",
    difficulty="HARD",
    tags=["array", "dynamic-programming"],
    description=(
        "You are given an array `prices` where `prices[i]` is the price of a stock "
        "on day `i`. Find the maximum profit you can achieve. You may complete at "
        "most two transactions. You must sell before you buy again."),
    constraints="- `1 <= prices.length <= 10^5`\n- `0 <= prices[i] <= 10^5`",
    time="O(n)", space="O(1)",
    fn="maxProfit", params=[("prices", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def maxProfit(self, prices):
        b1 = b2 = float("-inf")
        s1 = s2 = 0
        for p in prices:
            b1 = max(b1, -p)
            s1 = max(s1, b1 + p)
            b2 = max(b2, s1 - p)
            s2 = max(s2, b2 + p)
        return s2
""",
    examples=[
        {"input": {"prices": [3, 3, 5, 0, 0, 3, 1, 4]}, "note": "⇒ 6."},
        {"input": {"prices": [1, 2, 3, 4, 5]}, "note": "⇒ 4."},
        {"input": {"prices": [7, 6, 4, 3, 1]}, "note": "Không có lời ⇒ 0."},
    ],
    gen=lambda rng: [{"prices": ints(rng, rng.randint(1, 14), 0, 12)} for _ in range(24)],
)

_p(
    title="Best Time to Buy and Sell Stock IV",
    difficulty="HARD",
    tags=["array", "dynamic-programming"],
    description=(
        "You are given an integer `k` and an array `prices` where `prices[i]` is "
        "the price of a stock on day `i`. Find the maximum profit you can achieve "
        "with at most `k` transactions. You must sell before you buy again."),
    constraints="- `1 <= k <= 100`\n- `1 <= prices.length <= 1000`\n- `0 <= prices[i] <= 1000`",
    time="O(n*k)", space="O(k)",
    fn="maxProfit", params=[("k", "int"), ("prices", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def maxProfit(self, k, prices):
        if not prices or k == 0:
            return 0
        buy = [float("-inf")] * (k + 1)
        sell = [0] * (k + 1)
        for p in prices:
            for t in range(1, k + 1):
                buy[t] = max(buy[t], sell[t - 1] - p)
                sell[t] = max(sell[t], buy[t] + p)
        return sell[k]
""",
    examples=[
        {"input": {"k": 2, "prices": [2, 4, 1]}, "note": "⇒ 2."},
        {"input": {"k": 2, "prices": [3, 2, 6, 5, 0, 3]}, "note": "⇒ 7."},
        {"input": {"k": 1, "prices": [1]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"k": rng.randint(1, 4),
                      "prices": ints(rng, rng.randint(1, 12), 0, 12)} for _ in range(24)],
)

_p(
    title="Burst Balloons",
    difficulty="HARD",
    tags=["array", "dynamic-programming"],
    description=(
        "You are given `n` balloons indexed `0` to `n-1`, each painted with a "
        "number in `nums`. If you burst balloon `i` you get "
        "`nums[i-1] * nums[i] * nums[i+1]` coins (out-of-range neighbors count as "
        "1). Return the maximum coins you can collect by bursting all balloons "
        "wisely."),
    constraints="- `1 <= n <= 300`\n- `0 <= nums[i] <= 100`",
    time="O(n^3)", space="O(n^2)",
    fn="maxCoins", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def maxCoins(self, nums):
        a = [1] + nums + [1]
        n = len(a)
        dp = [[0] * n for _ in range(n)]
        for length in range(2, n):
            for l in range(0, n - length):
                r = l + length
                for m in range(l + 1, r):
                    dp[l][r] = max(dp[l][r],
                                   dp[l][m] + a[l] * a[m] * a[r] + dp[m][r])
        return dp[0][n - 1]
""",
    examples=[
        {"input": {"nums": [3, 1, 5, 8]}, "note": "⇒ 167."},
        {"input": {"nums": [1, 5]}, "note": "⇒ 10."},
        {"input": {"nums": [7]}, "note": "⇒ 7."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 8), 0, 9)} for _ in range(24)],
)

_p(
    title="Regular Expression Matching",
    difficulty="HARD",
    tags=["string", "dynamic-programming", "recursion"],
    description=(
        "Given an input string `s` and a pattern `p`, implement regular expression "
        "matching with support for '.' (matches any single character) and '*' "
        "(matches zero or more of the preceding element). The match should cover "
        "the entire input string."),
    constraints="- `1 <= s.length <= 20`\n- `1 <= p.length <= 30`\n- `s` is lowercase letters; `p` is letters, '.' and '*'.",
    time="O(n*m)", space="O(n*m)",
    fn="isMatch", params=[("s", "string"), ("p", "string")], **{"return": "boolean"},
    ref="""
class Solution:
    def isMatch(self, s, p):
        import functools
        @functools.lru_cache(None)
        def dp(i, j):
            if j == len(p):
                return i == len(s)
            first = i < len(s) and p[j] in (s[i], ".")
            if j + 1 < len(p) and p[j + 1] == "*":
                return dp(i, j + 2) or (first and dp(i + 1, j))
            return first and dp(i + 1, j + 1)
        return dp(0, 0)
""",
    examples=[
        {"input": {"s": "aa", "p": "a*"}, "note": "⇒ true."},
        {"input": {"s": "ab", "p": ".*"}, "note": "⇒ true."},
        {"input": {"s": "mississippi", "p": "mis*is*p*."}, "note": "⇒ false."},
    ],
    gen=lambda rng: [_regex_case(rng) for _ in range(26)],
)


def _regex_case(rng):
    s = "".join(rng.choice("ab") for _ in range(rng.randint(1, 5)))
    p = []
    for _ in range(rng.randint(1, 4)):
        p.append(rng.choice(["a", "b", ".", "a", "b"]))
        if rng.random() < 0.4:
            p.append("*")
    return {"s": s, "p": "".join(p) or "a"}


_p(
    title="Wildcard Matching",
    difficulty="HARD",
    tags=["string", "dynamic-programming", "greedy", "recursion"],
    description=(
        "Given an input string `s` and a pattern `p`, implement wildcard pattern "
        "matching with support for '?' (matches any single character) and '*' "
        "(matches any sequence of characters including the empty sequence). The "
        "match should cover the entire input string."),
    constraints="- `0 <= s.length, p.length <= 2000`\n- `s` is lowercase letters; `p` is letters, '?' and '*'.",
    time="O(n*m)", space="O(m)",
    fn="isMatch", params=[("s", "string"), ("p", "string")], **{"return": "boolean"},
    ref="""
class Solution:
    def isMatch(self, s, p):
        n, m = len(s), len(p)
        dp = [False] * (m + 1)
        dp[0] = True
        for j in range(1, m + 1):
            if p[j - 1] == "*":
                dp[j] = dp[j - 1]
            else:
                break
        for i in range(1, n + 1):
            prev = dp[0]
            dp[0] = False
            for j in range(1, m + 1):
                cur = dp[j]
                if p[j - 1] == "*":
                    dp[j] = dp[j - 1] or dp[j]
                elif p[j - 1] == "?" or p[j - 1] == s[i - 1]:
                    dp[j] = prev
                else:
                    dp[j] = False
                prev = cur
        return dp[m]
""",
    examples=[
        {"input": {"s": "aa", "p": "a"}, "note": "⇒ false."},
        {"input": {"s": "aa", "p": "*"}, "note": "⇒ true."},
        {"input": {"s": "cb", "p": "?a"}, "note": "⇒ false."},
    ],
    gen=lambda rng: [_wild_case(rng) for _ in range(26)],
)


def _wild_case(rng):
    s = "".join(rng.choice("ab") for _ in range(rng.randint(0, 5)))
    p = []
    for _ in range(rng.randint(0, 5)):
        p.append(rng.choice(["a", "b", "?", "*"]))
    return {"s": s, "p": "".join(p)}


# ── trees / lists (hard) ───────────────────────────────────────────────────
_p(
    title="Binary Tree Maximum Path Sum",
    difficulty="HARD",
    tags=["tree", "depth-first-search", "dynamic-programming", "binary-tree"],
    description=(
        "A path in a binary tree is a sequence of nodes where each pair of adjacent "
        "nodes has an edge, and a node appears at most once. The path sum is the "
        "sum of the node values. Given the `root`, return the maximum path sum of "
        "any non-empty path."),
    constraints="- The number of nodes is in the range `[1, 3*10^4]`.\n- `-1000 <= Node.val <= 1000`",
    time="O(n)", space="O(h)",
    fn="maxPathSum", params=[("root", "TreeNode")], **{"return": "int"},
    ref="""
class Solution:
    def maxPathSum(self, root):
        self.best = float("-inf")
        def gain(n):
            if not n:
                return 0
            l = max(gain(n.left), 0)
            r = max(gain(n.right), 0)
            self.best = max(self.best, n.val + l + r)
            return n.val + max(l, r)
        gain(root)
        return self.best
""",
    examples=[
        {"input": {"root": [1, 2, 3]}, "note": "2→1→3 ⇒ 6."},
        {"input": {"root": [-10, 9, 20, None, None, 15, 7]}, "note": "15→20→7 ⇒ 42."},
        {"input": {"root": [-3]}, "note": "⇒ -3."},
    ],
    gen=lambda rng: ([{"root": [rng.randint(-5, 5)]}]
                     + [{"root": tree_array(rng, rng.randint(1, 16), -8, 8)}
                        for _ in range(23)]),
)

_p(
    title="Reverse Nodes in k-Group",
    difficulty="HARD",
    tags=["linked-list", "recursion"],
    description=(
        "Given the `head` of a linked list, reverse the nodes of the list `k` at a "
        "time and return the modified list. Nodes left over at the end that are "
        "fewer than `k` remain as is."),
    constraints="- `1 <= k <= n <= 5000`\n- `0 <= Node.val <= 1000`",
    time="O(n)", space="O(1)",
    fn="reverseKGroup", params=[("head", "ListNode"), ("k", "int")],
    **{"return": "ListNode"},
    ref="""
class Solution:
    def reverseKGroup(self, head, k):
        node = head
        count = 0
        while node and count < k:
            node = node.next
            count += 1
        if count < k:
            return head
        prev = self.reverseKGroup(node, k)
        cur = head
        for _ in range(k):
            nxt = cur.next
            cur.next = prev
            prev = cur
            cur = nxt
        return prev
""",
    examples=[
        {"input": {"head": [1, 2, 3, 4, 5], "k": 2}, "note": "⇒ [2,1,4,3,5]."},
        {"input": {"head": [1, 2, 3, 4, 5], "k": 3}, "note": "⇒ [3,2,1,4,5]."},
        {"input": {"head": [1], "k": 1}, "note": "⇒ [1]."},
    ],
    gen=lambda rng: [_kgroup_case(rng) for _ in range(24)],
)


def _kgroup_case(rng):
    n = rng.randint(1, 12)
    return {"head": ints(rng, n, 0, 20), "k": rng.randint(1, n)}


# ── strings / bfs (hard) ───────────────────────────────────────────────────
_p(
    title="Minimum Window Substring",
    difficulty="HARD",
    tags=["hash-table", "string", "sliding-window"],
    description=(
        "Given two strings `s` and `t`, return the minimum window substring of `s` "
        "such that every character in `t` (including duplicates) is included in the "
        "window. If there is no such substring, return the empty string. The answer "
        "is guaranteed to be unique."),
    constraints="- `1 <= s.length, t.length <= 10^5`\n- Lowercase English letters.",
    time="O(n)", space="O(1)",
    fn="minWindow", params=[("s", "string"), ("t", "string")], **{"return": "string"},
    ref="""
class Solution:
    def minWindow(self, s, t):
        from collections import Counter
        need = Counter(t)
        missing = len(t)
        l = start = 0
        best = ""
        for r, c in enumerate(s):
            if need[c] > 0:
                missing -= 1
            need[c] -= 1
            while missing == 0:
                if not best or r - l + 1 < len(best):
                    best = s[l:r + 1]
                need[s[l]] += 1
                if need[s[l]] > 0:
                    missing += 1
                l += 1
        return best
""",
    examples=[
        {"input": {"s": "ADOBECODEBANC", "t": "ABC"}, "note": "⇒ \"BANC\"."},
        {"input": {"s": "a", "t": "a"}, "note": "⇒ \"a\"."},
        {"input": {"s": "a", "t": "aa"}, "note": "Không đủ ⇒ \"\"."},
    ],
    gen=lambda rng: [{"s": lower(rng, rng.randint(1, 12)),
                      "t": lower(rng, rng.randint(1, 4))} for _ in range(24)],
)

_p(
    title="Word Ladder",
    difficulty="HARD",
    tags=["hash-table", "string", "breadth-first-search"],
    description=(
        "Given two words `beginWord` and `endWord`, and a dictionary `wordList`, "
        "return the number of words in the shortest transformation sequence from "
        "`beginWord` to `endWord`, changing one letter at a time such that each "
        "intermediate word is in `wordList`. Return 0 if no such sequence exists. "
        "The sequence length counts both endpoints."),
    constraints="- `1 <= beginWord.length <= 10`\n- All words have the same length and are lowercase.",
    time="O(N * L^2)", space="O(N * L)",
    fn="ladderLength",
    params=[("beginWord", "string"), ("endWord", "string"), ("wordList", "string[]")],
    **{"return": "int"},
    ref="""
class Solution:
    def ladderLength(self, beginWord, endWord, wordList):
        from collections import deque
        words = set(wordList)
        if endWord not in words:
            return 0
        q = deque([(beginWord, 1)])
        seen = {beginWord}
        while q:
            word, steps = q.popleft()
            if word == endWord:
                return steps
            for i in range(len(word)):
                for ch in "abcdefghijklmnopqrstuvwxyz":
                    nxt = word[:i] + ch + word[i + 1:]
                    if nxt in words and nxt not in seen:
                        seen.add(nxt)
                        q.append((nxt, steps + 1))
        return 0
""",
    examples=[
        {"input": {"beginWord": "hit", "endWord": "cog",
                   "wordList": ["hot", "dot", "dog", "lot", "log", "cog"]},
         "note": "hit→hot→dot→dog→cog ⇒ 5."},
        {"input": {"beginWord": "hit", "endWord": "cog",
                   "wordList": ["hot", "dot", "dog", "lot", "log"]},
         "note": "Thiếu \"cog\" ⇒ 0."},
        {"input": {"beginWord": "a", "endWord": "c", "wordList": ["a", "b", "c"]},
         "note": "a→c ⇒ 2."},
    ],
    gen=lambda rng: [_ladder_case(rng) for _ in range(26)],
)


def _ladder_case(rng):
    L = rng.randint(2, 3)
    pool = list({lower(rng, L) for _ in range(rng.randint(3, 8))})
    begin = lower(rng, L)
    end = rng.choice(pool)
    return {"beginWord": begin, "endWord": end, "wordList": pool}


# ── matrix / graph (hard) ──────────────────────────────────────────────────
_p(
    title="Longest Increasing Path in a Matrix",
    difficulty="HARD",
    tags=["array", "dynamic-programming", "depth-first-search", "memoization", "matrix"],
    description=(
        "Given an `m x n` integers `matrix`, return the length of the longest "
        "strictly increasing path. From each cell you may move in four directions "
        "(up, down, left, right); you may not move diagonally or outside the grid."),
    constraints="- `1 <= m, n <= 200`\n- `0 <= matrix[i][j] <= 2^31 - 1`",
    time="O(m*n)", space="O(m*n)",
    fn="longestIncreasingPath", params=[("matrix", "int[][]")], **{"return": "int"},
    ref="""
class Solution:
    def longestIncreasingPath(self, matrix):
        import functools
        R, C = len(matrix), len(matrix[0])
        @functools.lru_cache(None)
        def dfs(r, c):
            best = 1
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nr, nc = r + dr, c + dc
                if 0 <= nr < R and 0 <= nc < C and matrix[nr][nc] > matrix[r][c]:
                    best = max(best, 1 + dfs(nr, nc))
            return best
        return max(dfs(r, c) for r in range(R) for c in range(C))
""",
    examples=[
        {"input": {"matrix": [[9, 9, 4], [6, 6, 8], [2, 1, 1]]},
         "note": "1→2→6→9 ⇒ 4."},
        {"input": {"matrix": [[3, 4, 5], [3, 2, 6], [2, 2, 1]]}, "note": "⇒ 4."},
        {"input": {"matrix": [[1]]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [{"matrix": matrix(rng, rng.randint(1, 5), rng.randint(1, 5), 0, 9)}
                     for _ in range(24)],
)

_p(
    title="Pacific Atlantic Water Flow",
    difficulty="HARD",
    tags=["array", "depth-first-search", "breadth-first-search", "matrix"],
    description=(
        "Given an `m x n` matrix `heights` of island cell heights, the Pacific "
        "ocean touches the top and left edges, the Atlantic touches the bottom and "
        "right edges. Water flows from a cell to neighbors of height less than or "
        "equal to it. Return a list of grid coordinates `[r, c]` from which water "
        "can flow to both oceans."),
    constraints="- `m == heights.length`, `n == heights[i].length`\n- `1 <= m, n <= 200`",
    time="O(m*n)", space="O(m*n)",
    fn="pacificAtlantic", params=[("heights", "int[][]")],
    **{"return": "List<List<Integer>>"}, orderMatters=False,
    ref="""
class Solution:
    def pacificAtlantic(self, heights):
        R, C = len(heights), len(heights[0])
        def bfs(starts):
            from collections import deque
            seen = set(starts)
            q = deque(starts)
            while q:
                r, c = q.popleft()
                for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nr, nc = r + dr, c + dc
                    if (0 <= nr < R and 0 <= nc < C and (nr, nc) not in seen
                            and heights[nr][nc] >= heights[r][c]):
                        seen.add((nr, nc))
                        q.append((nr, nc))
            return seen
        pac = bfs([(0, c) for c in range(C)] + [(r, 0) for r in range(R)])
        atl = bfs([(R - 1, c) for c in range(C)] + [(r, C - 1) for r in range(R)])
        return [[r, c] for r, c in (pac & atl)]
""",
    examples=[
        {"input": {"heights": [[1, 2, 2, 3, 5], [3, 2, 3, 4, 4], [2, 4, 5, 3, 1],
                               [6, 7, 1, 4, 5], [5, 1, 1, 2, 4]]},
         "note": "Các ô chảy được ra cả hai đại dương."},
        {"input": {"heights": [[1]]}, "note": "Ô duy nhất ⇒ [[0,0]]."},
        {"input": {"heights": [[2, 1], [1, 2]]}, "note": "Bốn góc."},
    ],
    gen=lambda rng: [{"heights": matrix(rng, rng.randint(1, 4), rng.randint(1, 4), 1, 6)}
                     for _ in range(24)],
)

_p(
    title="Min Cost to Connect All Points",
    difficulty="HARD",
    tags=["array", "union-find", "graph", "minimum-spanning-tree"],
    description=(
        "You are given an array `points` representing integer coordinates "
        "`points[i] = [xi, yi]`. The cost of connecting two points is their "
        "Manhattan distance. Return the minimum cost to connect all points so that "
        "there is exactly one path between any two points."),
    constraints="- `1 <= points.length <= 1000`\n- `-10^6 <= xi, yi <= 10^6`",
    time="O(n^2)", space="O(n)",
    fn="minCostConnectPoints", params=[("points", "int[][]")], **{"return": "int"},
    ref="""
class Solution:
    def minCostConnectPoints(self, points):
        import heapq
        n = len(points)
        if n <= 1:
            return 0
        seen = set()
        pq = [(0, 0)]
        total = 0
        while len(seen) < n:
            d, u = heapq.heappop(pq)
            if u in seen:
                continue
            seen.add(u)
            total += d
            for v in range(n):
                if v not in seen:
                    dist = abs(points[u][0] - points[v][0]) + abs(points[u][1] - points[v][1])
                    heapq.heappush(pq, (dist, v))
        return total
""",
    examples=[
        {"input": {"points": [[0, 0], [2, 2], [3, 10], [5, 2], [7, 0]]},
         "note": "⇒ 20."},
        {"input": {"points": [[3, 12], [-2, 5], [-4, 1]]}, "note": "⇒ 18."},
        {"input": {"points": [[0, 0]]}, "note": "Một điểm ⇒ 0."},
    ],
    gen=lambda rng: [{"points": [[rng.randint(-9, 9), rng.randint(-9, 9)]
                                 for _ in range(rng.randint(1, 7))]} for _ in range(24)],
)

_p(
    title="Network Delay Time",
    difficulty="HARD",
    tags=["depth-first-search", "breadth-first-search", "graph", "shortest-path", "heap"],
    description=(
        "You are given a network of `n` nodes labeled `1` to `n`, and `times` where "
        "`times[i] = [u, v, w]` is a directed edge from `u` to `v` with travel time "
        "`w`. We send a signal from node `k`. Return the minimum time for all nodes "
        "to receive the signal, or `-1` if impossible."),
    constraints="- `1 <= k <= n <= 100`\n- `1 <= times.length <= 6000`",
    time="O(E log V)", space="O(V + E)",
    fn="networkDelayTime",
    params=[("times", "int[][]"), ("n", "int"), ("k", "int")], **{"return": "int"},
    ref="""
class Solution:
    def networkDelayTime(self, times, n, k):
        import heapq
        from collections import defaultdict
        adj = defaultdict(list)
        for u, v, w in times:
            adj[u].append((v, w))
        dist = {}
        pq = [(0, k)]
        while pq:
            d, u = heapq.heappop(pq)
            if u in dist:
                continue
            dist[u] = d
            for v, w in adj[u]:
                if v not in dist:
                    heapq.heappush(pq, (d + w, v))
        return max(dist.values()) if len(dist) == n else -1
""",
    examples=[
        {"input": {"times": [[2, 1, 1], [2, 3, 1], [3, 4, 1]], "n": 4, "k": 2},
         "note": "⇒ 2."},
        {"input": {"times": [[1, 2, 1]], "n": 2, "k": 1}, "note": "⇒ 1."},
        {"input": {"times": [[1, 2, 1]], "n": 2, "k": 2},
         "note": "Không tới node 1 ⇒ -1."},
    ],
    gen=lambda rng: [_delay_case(rng) for _ in range(26)],
)


def _delay_case(rng):
    n = rng.randint(1, 6)
    times = []
    for _ in range(rng.randint(1, n * 2)):
        u, v = rng.randint(1, n), rng.randint(1, n)
        if u != v:
            times.append([u, v, rng.randint(1, 6)])
    if not times:
        times.append([1, 1 if n == 1 else 2, 1])
        if n == 1:
            times = [[1, 1, 1]]
    return {"times": times, "n": n, "k": rng.randint(1, n)}


_p(
    title="Cheapest Flights Within K Stops",
    difficulty="HARD",
    tags=["dynamic-programming", "breadth-first-search", "graph", "shortest-path"],
    description=(
        "There are `n` cities connected by `flights` where "
        "`flights[i] = [from, to, price]`. Given `src`, `dst`, and `k`, return the "
        "cheapest price from `src` to `dst` with at most `k` stops. If there is no "
        "such route, return `-1`."),
    constraints="- `1 <= n <= 100`\n- `0 <= flights.length <= n*(n-1)/2`\n- `0 <= src, dst, k < n`",
    time="O(k*E)", space="O(n)",
    fn="findCheapestPrice",
    params=[("n", "int"), ("flights", "int[][]"), ("src", "int"), ("dst", "int"),
            ("k", "int")], **{"return": "int"},
    ref="""
class Solution:
    def findCheapestPrice(self, n, flights, src, dst, k):
        INF = float("inf")
        dist = [INF] * n
        dist[src] = 0
        for _ in range(k + 1):
            tmp = dist[:]
            for u, v, w in flights:
                if dist[u] != INF and dist[u] + w < tmp[v]:
                    tmp[v] = dist[u] + w
            dist = tmp
        return dist[dst] if dist[dst] != INF else -1
""",
    examples=[
        {"input": {"n": 4, "flights": [[0, 1, 100], [1, 2, 100], [2, 0, 100],
                                       [1, 3, 600], [2, 3, 200]],
                   "src": 0, "dst": 3, "k": 1}, "note": "⇒ 700."},
        {"input": {"n": 3, "flights": [[0, 1, 100], [1, 2, 100], [0, 2, 500]],
                   "src": 0, "dst": 2, "k": 1}, "note": "⇒ 200."},
        {"input": {"n": 3, "flights": [[0, 1, 100], [1, 2, 100], [0, 2, 500]],
                   "src": 0, "dst": 2, "k": 0}, "note": "⇒ 500."},
    ],
    gen=lambda rng: [_flights_case(rng) for _ in range(26)],
)


def _flights_case(rng):
    n = rng.randint(2, 6)
    flights = []
    for _ in range(rng.randint(0, n * 2)):
        u, v = rng.randint(0, n - 1), rng.randint(0, n - 1)
        if u != v:
            flights.append([u, v, rng.randint(1, 200)])
    src, dst = rng.sample(range(n), 2)
    return {"n": n, "flights": flights, "src": src, "dst": dst, "k": rng.randint(0, n - 1)}


_p(
    title="Russian Doll Envelopes",
    difficulty="HARD",
    tags=["array", "binary-search", "dynamic-programming", "sorting"],
    description=(
        "You are given a 2D array of `envelopes` where "
        "`envelopes[i] = [wi, hi]`. One envelope can fit into another if and only "
        "if both its width and height are strictly greater. Return the maximum "
        "number of envelopes you can Russian-doll (nest)."),
    constraints="- `1 <= envelopes.length <= 10^5`\n- `1 <= wi, hi <= 10^5`",
    time="O(n log n)", space="O(n)",
    fn="maxEnvelopes", params=[("envelopes", "int[][]")], **{"return": "int"},
    ref="""
class Solution:
    def maxEnvelopes(self, envelopes):
        import bisect
        envelopes.sort(key=lambda x: (x[0], -x[1]))
        tails = []
        for _, h in envelopes:
            i = bisect.bisect_left(tails, h)
            if i == len(tails):
                tails.append(h)
            else:
                tails[i] = h
        return len(tails)
""",
    examples=[
        {"input": {"envelopes": [[5, 4], [6, 4], [6, 7], [2, 3]]}, "note": "⇒ 3."},
        {"input": {"envelopes": [[1, 1], [1, 1], [1, 1]]}, "note": "⇒ 1."},
        {"input": {"envelopes": [[4, 5]]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [{"envelopes": [[rng.randint(1, 8), rng.randint(1, 8)]
                                    for _ in range(rng.randint(1, 8))]} for _ in range(24)],
)

_p(
    title="Count of Smaller Numbers After Self",
    difficulty="HARD",
    tags=["array", "binary-search", "divide-and-conquer", "merge-sort", "binary-indexed-tree"],
    description=(
        "Given an integer array `nums`, return an array `counts` where `counts[i]` "
        "is the number of elements to the right of `nums[i]` that are smaller than "
        "`nums[i]`."),
    constraints="- `1 <= nums.length <= 10^5`\n- `-10^4 <= nums[i] <= 10^4`",
    time="O(n log n)", space="O(n)",
    fn="countSmaller", params=[("nums", "int[]")], **{"return": "List<Integer>"},
    orderMatters=True,
    ref="""
class Solution:
    def countSmaller(self, nums):
        import bisect
        seen = []
        res = [0] * len(nums)
        for i in range(len(nums) - 1, -1, -1):
            pos = bisect.bisect_left(seen, nums[i])
            res[i] = pos
            seen.insert(pos, nums[i])
        return res
""",
    examples=[
        {"input": {"nums": [5, 2, 6, 1]}, "note": "⇒ [2,1,1,0]."},
        {"input": {"nums": [-1, -1]}, "note": "⇒ [0,0]."},
        {"input": {"nums": [3]}, "note": "⇒ [0]."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 14), -9, 9)} for _ in range(24)],
)
