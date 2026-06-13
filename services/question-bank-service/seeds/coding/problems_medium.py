"""MEDIUM problems — NeetCode-popular, deterministic, supported types only.

Ambiguity guard: problems whose answer is a collection of collections only use
`orderMatters=False` for the OUTER ordering and emit each inner group in a
canonical form (e.g. 3Sum triplets sorted ascending) so the exact-match judge
stays correct regardless of which valid solution a candidate writes.
"""
from __future__ import annotations

from helpers import ints, unique_ints, lower, tree_array, bst_array

PROBLEMS = []


def _p(**kw):
    PROBLEMS.append(kw)


def matrix(rng, r, c, lo, hi):
    return [[rng.randint(lo, hi) for _ in range(c)] for _ in range(r)]


def grid01(rng, r, c, p1=0.5):
    return [["1" if rng.random() < p1 else "0" for _ in range(c)] for _ in range(r)]


# ── arrays / two pointers ──────────────────────────────────────────────────
_p(
    title="3Sum",
    difficulty="MEDIUM",
    tags=["array", "two-pointers", "sorting"],
    description=(
        "Given an integer array `nums`, return all the triplets "
        "`[nums[i], nums[j], nums[k]]` such that `i != j`, `i != k`, `j != k`, and "
        "`nums[i] + nums[j] + nums[k] == 0`. The solution set must not contain "
        "duplicate triplets. Each returned triplet must be sorted in "
        "non-decreasing order."),
    constraints="- `3 <= nums.length <= 3000`\n- `-10^5 <= nums[i] <= 10^5`",
    time="O(n^2)", space="O(1)",
    fn="threeSum", params=[("nums", "int[]")], **{"return": "List<List<Integer>>"},
    orderMatters=False,
    ref="""
class Solution:
    def threeSum(self, nums):
        nums = sorted(nums)
        n = len(nums)
        res = []
        for i in range(n):
            if i > 0 and nums[i] == nums[i - 1]:
                continue
            l, r = i + 1, n - 1
            while l < r:
                s = nums[i] + nums[l] + nums[r]
                if s < 0:
                    l += 1
                elif s > 0:
                    r -= 1
                else:
                    res.append([nums[i], nums[l], nums[r]])
                    l += 1
                    r -= 1
                    while l < r and nums[l] == nums[l - 1]:
                        l += 1
                    while l < r and nums[r] == nums[r + 1]:
                        r -= 1
        return res
""",
    examples=[
        {"input": {"nums": [-1, 0, 1, 2, -1, -4]},
         "note": "⇒ [[-1,-1,2],[-1,0,1]]."},
        {"input": {"nums": [0, 1, 1]}, "note": "Không bộ ba nào tổng 0 ⇒ []."},
        {"input": {"nums": [0, 0, 0]}, "note": "⇒ [[0,0,0]]."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(3, 12), -6, 6)} for _ in range(24)],
)

_p(
    title="Find the Duplicate Number",
    difficulty="MEDIUM",
    tags=["array", "two-pointers", "binary-search", "bit-manipulation"],
    description=(
        "Given an array `nums` of `n + 1` integers where each integer is in the "
        "range `[1, n]` inclusive, there is only one repeated number. Return this "
        "repeated number. You must not modify the array and use only constant "
        "extra space."),
    constraints="- `1 <= n <= 10^5`\n- Exactly one value is repeated (one or more times).",
    time="O(n)", space="O(1)",
    fn="findDuplicate", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def findDuplicate(self, nums):
        slow = fast = nums[0]
        while True:
            slow = nums[slow]
            fast = nums[nums[fast]]
            if slow == fast:
                break
        slow = nums[0]
        while slow != fast:
            slow = nums[slow]
            fast = nums[fast]
        return slow
""",
    examples=[
        {"input": {"nums": [1, 3, 4, 2, 2]}, "note": "2 lặp lại ⇒ 2."},
        {"input": {"nums": [3, 1, 3, 4, 2]}, "note": "3 lặp lại ⇒ 3."},
        {"input": {"nums": [1, 1]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [_dup_case(rng) for _ in range(24)],
)


def _dup_case(rng):
    n = rng.randint(1, 20)
    arr = list(range(1, n + 1))
    dup = rng.randint(1, n)
    arr.append(dup)
    rng.shuffle(arr)
    return {"nums": arr}


_p(
    title="Subarray Sum Equals K",
    difficulty="MEDIUM",
    tags=["array", "hash-table", "prefix-sum"],
    description=(
        "Given an array of integers `nums` and an integer `k`, return the total "
        "number of contiguous subarrays whose sum equals `k`."),
    constraints="- `1 <= nums.length <= 2*10^4`\n- `-1000 <= nums[i] <= 1000`\n- `-10^7 <= k <= 10^7`",
    time="O(n)", space="O(n)",
    fn="subarraySum", params=[("nums", "int[]"), ("k", "int")], **{"return": "int"},
    ref="""
class Solution:
    def subarraySum(self, nums, k):
        from collections import defaultdict
        seen = defaultdict(int)
        seen[0] = 1
        cur = ans = 0
        for v in nums:
            cur += v
            ans += seen[cur - k]
            seen[cur] += 1
        return ans
""",
    examples=[
        {"input": {"nums": [1, 1, 1], "k": 2}, "note": "[1,1] hai lần ⇒ 2."},
        {"input": {"nums": [1, 2, 3], "k": 3}, "note": "[3] và [1,2] ⇒ 2."},
        {"input": {"nums": [1], "k": 0}, "note": "Không có ⇒ 0."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 12), -3, 3),
                      "k": rng.randint(-4, 6)} for _ in range(24)],
)

_p(
    title="Find All Numbers Disappeared in an Array",
    difficulty="MEDIUM",
    tags=["array", "hash-table"],
    description=(
        "Given an array `nums` of `n` integers where `nums[i]` is in the range "
        "`[1, n]`, return an array of all the integers in the range `[1, n]` that "
        "do not appear in `nums`."),
    constraints="- `n == nums.length`\n- `1 <= n <= 10^5`\n- `1 <= nums[i] <= n`",
    time="O(n)", space="O(1)",
    fn="findDisappearedNumbers", params=[("nums", "int[]")],
    **{"return": "List<Integer>"}, orderMatters=False,
    ref="""
class Solution:
    def findDisappearedNumbers(self, nums):
        s = set(nums)
        return [x for x in range(1, len(nums) + 1) if x not in s]
""",
    examples=[
        {"input": {"nums": [4, 3, 2, 7, 8, 2, 3, 1]}, "note": "Thiếu 5 và 6 ⇒ [5,6]."},
        {"input": {"nums": [1, 1]}, "note": "Thiếu 2 ⇒ [2]."},
        {"input": {"nums": [1, 2, 3]}, "note": "Không thiếu ⇒ []."},
    ],
    gen=lambda rng: [_disappeared_case(rng) for _ in range(24)],
)


def _disappeared_case(rng):
    n = rng.randint(1, 14)
    return {"nums": [rng.randint(1, n) for _ in range(n)]}


_p(
    title="Maximum Product Subarray",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "Given an integer array `nums`, find a contiguous non-empty subarray that "
        "has the largest product, and return that product."),
    constraints="- `1 <= nums.length <= 2*10^4`\n- `-10 <= nums[i] <= 10`",
    time="O(n)", space="O(1)",
    fn="maxProduct", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def maxProduct(self, nums):
        res = nums[0]
        cur_max = cur_min = nums[0]
        for v in nums[1:]:
            cands = (v, cur_max * v, cur_min * v)
            cur_max = max(cands)
            cur_min = min(cands)
            res = max(res, cur_max)
        return res
""",
    examples=[
        {"input": {"nums": [2, 3, -2, 4]}, "note": "[2,3] ⇒ 6."},
        {"input": {"nums": [-2, 0, -1]}, "note": "⇒ 0."},
        {"input": {"nums": [-2, 3, -4]}, "note": "Cả ba ⇒ 24."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 10), -4, 4)} for _ in range(24)],
)

_p(
    title="Find Minimum in Rotated Sorted Array",
    difficulty="MEDIUM",
    tags=["array", "binary-search"],
    description=(
        "Suppose an array of unique integers sorted in ascending order is rotated "
        "between 1 and n times. Given the rotated array `nums`, return the minimum "
        "element. You must write an algorithm that runs in O(log n) time."),
    constraints="- `1 <= nums.length <= 5000`\n- All integers are unique.",
    time="O(log n)", space="O(1)",
    fn="findMin", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def findMin(self, nums):
        l, r = 0, len(nums) - 1
        while l < r:
            m = (l + r) // 2
            if nums[m] > nums[r]:
                l = m + 1
            else:
                r = m
        return nums[l]
""",
    examples=[
        {"input": {"nums": [3, 4, 5, 1, 2]}, "note": "⇒ 1."},
        {"input": {"nums": [4, 5, 6, 7, 0, 1, 2]}, "note": "⇒ 0."},
        {"input": {"nums": [11, 13, 15, 17]}, "note": "Không xoay ⇒ 11."},
    ],
    gen=lambda rng: [_rotated_case(rng) for _ in range(24)],
)


def _rotated_case(rng):
    n = rng.randint(1, 12)
    vals = sorted(unique_ints(rng, n, -30, 30))
    k = rng.randint(0, n - 1)
    return {"nums": vals[k:] + vals[:k]}


# ── intervals ──────────────────────────────────────────────────────────────
_p(
    title="Merge Intervals",
    difficulty="MEDIUM",
    tags=["array", "sorting"],
    description=(
        "Given an array of `intervals` where `intervals[i] = [start_i, end_i]`, "
        "merge all overlapping intervals and return an array of the "
        "non-overlapping intervals that cover all the intervals in the input."),
    constraints="- `1 <= intervals.length <= 10^4`\n- `intervals[i].length == 2`\n- `0 <= start_i <= end_i <= 10^4`",
    time="O(n log n)", space="O(n)",
    fn="merge", params=[("intervals", "int[][]")], **{"return": "int[][]"},
    orderMatters=False,
    ref="""
class Solution:
    def merge(self, intervals):
        intervals = sorted(intervals)
        res = []
        for s, e in intervals:
            if res and s <= res[-1][1]:
                res[-1][1] = max(res[-1][1], e)
            else:
                res.append([s, e])
        return res
""",
    examples=[
        {"input": {"intervals": [[1, 3], [2, 6], [8, 10], [15, 18]]},
         "note": "[1,3]&[2,6] gộp ⇒ [[1,6],[8,10],[15,18]]."},
        {"input": {"intervals": [[1, 4], [4, 5]]}, "note": "Chạm nhau ⇒ [[1,5]]."},
        {"input": {"intervals": [[1, 4]]}, "note": "⇒ [[1,4]]."},
    ],
    gen=lambda rng: [{"intervals": _intervals(rng)} for _ in range(24)],
)


def _intervals(rng):
    out = []
    for _ in range(rng.randint(1, 7)):
        s = rng.randint(0, 15)
        out.append([s, s + rng.randint(0, 6)])
    return out


_p(
    title="Insert Interval",
    difficulty="MEDIUM",
    tags=["array"],
    description=(
        "You are given a set of non-overlapping `intervals` sorted by start, and "
        "an interval `newInterval`. Insert `newInterval` so the result remains "
        "sorted and non-overlapping (merging if necessary). Return the result."),
    constraints="- `0 <= intervals.length <= 10^4`\n- intervals are sorted and non-overlapping.",
    time="O(n)", space="O(n)",
    fn="insert", params=[("intervals", "int[][]"), ("newInterval", "int[]")],
    **{"return": "int[][]"}, orderMatters=True,
    ref="""
class Solution:
    def insert(self, intervals, newInterval):
        res = []
        i, n = 0, len(intervals)
        while i < n and intervals[i][1] < newInterval[0]:
            res.append(intervals[i]); i += 1
        s, e = newInterval
        while i < n and intervals[i][0] <= e:
            s = min(s, intervals[i][0]); e = max(e, intervals[i][1]); i += 1
        res.append([s, e])
        while i < n:
            res.append(intervals[i]); i += 1
        return res
""",
    examples=[
        {"input": {"intervals": [[1, 3], [6, 9]], "newInterval": [2, 5]},
         "note": "⇒ [[1,5],[6,9]]."},
        {"input": {"intervals": [[1, 2], [3, 5], [6, 7], [8, 10]],
                   "newInterval": [4, 8]}, "note": "⇒ [[1,2],[3,10]]."},
        {"input": {"intervals": [], "newInterval": [5, 7]}, "note": "⇒ [[5,7]]."},
    ],
    gen=lambda rng: [_insert_case(rng) for _ in range(24)],
)


def _insert_case(rng):
    cur, out = 0, []
    for _ in range(rng.randint(0, 6)):
        s = cur + rng.randint(1, 3)
        e = s + rng.randint(0, 3)
        out.append([s, e]); cur = e
    s = rng.randint(0, 20)
    return {"intervals": out, "newInterval": [s, s + rng.randint(0, 5)]}


_p(
    title="Non-overlapping Intervals",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming", "greedy", "sorting"],
    description=(
        "Given an array of `intervals`, return the minimum number of intervals you "
        "need to remove to make the rest non-overlapping. Intervals that touch at "
        "an endpoint (e.g. [1,2] and [2,3]) are not considered overlapping."),
    constraints="- `1 <= intervals.length <= 10^5`\n- `intervals[i].length == 2`",
    time="O(n log n)", space="O(1)",
    fn="eraseOverlapIntervals", params=[("intervals", "int[][]")],
    **{"return": "int"},
    ref="""
class Solution:
    def eraseOverlapIntervals(self, intervals):
        intervals.sort(key=lambda x: x[1])
        prev_end = float("-inf")
        keep = 0
        for s, e in intervals:
            if s >= prev_end:
                keep += 1
                prev_end = e
        return len(intervals) - keep
""",
    examples=[
        {"input": {"intervals": [[1, 2], [2, 3], [3, 4], [1, 3]]},
         "note": "Bỏ [1,3] ⇒ 1."},
        {"input": {"intervals": [[1, 2], [1, 2], [1, 2]]}, "note": "Bỏ 2 ⇒ 2."},
        {"input": {"intervals": [[1, 2], [2, 3]]}, "note": "Không chồng ⇒ 0."},
    ],
    gen=lambda rng: [{"intervals": _intervals(rng)} for _ in range(24)],
)

# ── matrix ─────────────────────────────────────────────────────────────────
_p(
    title="Sort Colors",
    difficulty="MEDIUM",
    tags=["array", "two-pointers", "sorting"],
    description=(
        "Given an array `nums` with `n` objects colored red, white, or blue "
        "(represented by 0, 1, and 2), sort them in-place so objects of the same "
        "color are adjacent, in the order red, white, blue. You must solve it "
        "without using the library sort."),
    constraints="- `n == nums.length`\n- `1 <= n <= 300`\n- `nums[i]` is 0, 1, or 2.",
    time="O(n)", space="O(1)",
    fn="sortColors", params=[("nums", "int[]")], **{"return": "void"},
    inPlace=True, orderMatters=True,
    ref="""
class Solution:
    def sortColors(self, nums):
        lo, i, hi = 0, 0, len(nums) - 1
        while i <= hi:
            if nums[i] == 0:
                nums[lo], nums[i] = nums[i], nums[lo]; lo += 1; i += 1
            elif nums[i] == 2:
                nums[hi], nums[i] = nums[i], nums[hi]; hi -= 1
            else:
                i += 1
""",
    examples=[
        {"input": {"nums": [2, 0, 2, 1, 1, 0]}, "note": "⇒ [0,0,1,1,2,2]."},
        {"input": {"nums": [2, 0, 1]}, "note": "⇒ [0,1,2]."},
        {"input": {"nums": [0]}, "note": "⇒ [0]."},
    ],
    gen=lambda rng: [{"nums": [rng.randint(0, 2) for _ in range(rng.randint(1, 12))]}
                     for _ in range(24)],
)

_p(
    title="Rotate Image",
    difficulty="MEDIUM",
    tags=["array", "math", "matrix"],
    description=(
        "You are given an `n x n` 2D `matrix` representing an image. Rotate the "
        "image by 90 degrees clockwise. You have to rotate the image in-place."),
    constraints="- `n == matrix.length == matrix[i].length`\n- `1 <= n <= 20`",
    time="O(n^2)", space="O(1)",
    fn="rotate", params=[("matrix", "int[][]")], **{"return": "void"},
    inPlace=True, orderMatters=True,
    ref="""
class Solution:
    def rotate(self, matrix):
        matrix[:] = [list(row) for row in zip(*matrix[::-1])]
""",
    examples=[
        {"input": {"matrix": [[1, 2, 3], [4, 5, 6], [7, 8, 9]]},
         "note": "⇒ [[7,4,1],[8,5,2],[9,6,3]]."},
        {"input": {"matrix": [[1, 2], [3, 4]]}, "note": "⇒ [[3,1],[4,2]]."},
        {"input": {"matrix": [[1]]}, "note": "⇒ [[1]]."},
    ],
    gen=lambda rng: [{"matrix": matrix(rng, n, n, 0, 9)}
                     for n in [rng.randint(1, 5) for _ in range(24)]],
)

_p(
    title="Spiral Matrix",
    difficulty="MEDIUM",
    tags=["array", "matrix", "simulation"],
    description=(
        "Given an `m x n` `matrix`, return all elements of the matrix in spiral "
        "order (clockwise starting from the top-left)."),
    constraints="- `1 <= m, n <= 10`\n- `-100 <= matrix[i][j] <= 100`",
    time="O(m*n)", space="O(1)",
    fn="spiralOrder", params=[("matrix", "int[][]")], **{"return": "List<Integer>"},
    orderMatters=True,
    ref="""
class Solution:
    def spiralOrder(self, matrix):
        res = []
        while matrix:
            res += matrix.pop(0)
            matrix = [list(r) for r in zip(*matrix)][::-1]
        return res
""",
    examples=[
        {"input": {"matrix": [[1, 2, 3], [4, 5, 6], [7, 8, 9]]},
         "note": "⇒ [1,2,3,6,9,8,7,4,5]."},
        {"input": {"matrix": [[1, 2], [3, 4]]}, "note": "⇒ [1,2,4,3]."},
        {"input": {"matrix": [[7]]}, "note": "⇒ [7]."},
    ],
    gen=lambda rng: [{"matrix": matrix(rng, rng.randint(1, 5), rng.randint(1, 5), -9, 9)}
                     for _ in range(24)],
)

_p(
    title="Set Matrix Zeroes",
    difficulty="MEDIUM",
    tags=["array", "hash-table", "matrix"],
    description=(
        "Given an `m x n` integer `matrix`, if an element is 0, set its entire row "
        "and column to 0. You must do it in-place."),
    constraints="- `1 <= m, n <= 200`\n- `-2^31 <= matrix[i][j] <= 2^31 - 1`",
    time="O(m*n)", space="O(1)",
    fn="setZeroes", params=[("matrix", "int[][]")], **{"return": "void"},
    inPlace=True, orderMatters=True,
    ref="""
class Solution:
    def setZeroes(self, matrix):
        rows, cols = set(), set()
        for i, row in enumerate(matrix):
            for j, v in enumerate(row):
                if v == 0:
                    rows.add(i); cols.add(j)
        for i in range(len(matrix)):
            for j in range(len(matrix[0])):
                if i in rows or j in cols:
                    matrix[i][j] = 0
""",
    examples=[
        {"input": {"matrix": [[1, 1, 1], [1, 0, 1], [1, 1, 1]]},
         "note": "⇒ [[1,0,1],[0,0,0],[1,0,1]]."},
        {"input": {"matrix": [[0, 1], [1, 1]]}, "note": "⇒ [[0,0],[0,1]]."},
        {"input": {"matrix": [[5]]}, "note": "⇒ [[5]]."},
    ],
    gen=lambda rng: [{"matrix": _zero_matrix(rng)} for _ in range(24)],
)


def _zero_matrix(rng):
    r, c = rng.randint(1, 4), rng.randint(1, 4)
    return [[rng.choice([0, 0, rng.randint(1, 9)]) for _ in range(c)] for _ in range(r)]

_p(
    title="Search a 2D Matrix",
    difficulty="MEDIUM",
    tags=["array", "binary-search", "matrix"],
    description=(
        "You are given an `m x n` integer matrix with the properties: each row is "
        "sorted in non-decreasing order, and the first integer of each row is "
        "greater than the last integer of the previous row. Given an integer "
        "`target`, return `true` if `target` is in the matrix."),
    constraints="- `1 <= m, n <= 100`\n- `-10^4 <= matrix[i][j], target <= 10^4`",
    time="O(log(m*n))", space="O(1)",
    fn="searchMatrix", params=[("matrix", "int[][]"), ("target", "int")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def searchMatrix(self, matrix, target):
        m, n = len(matrix), len(matrix[0])
        lo, hi = 0, m * n - 1
        while lo <= hi:
            mid = (lo + hi) // 2
            v = matrix[mid // n][mid % n]
            if v == target:
                return True
            if v < target:
                lo = mid + 1
            else:
                hi = mid - 1
        return False
""",
    examples=[
        {"input": {"matrix": [[1, 3, 5, 7], [10, 11, 16, 20], [23, 30, 34, 60]],
                   "target": 3}, "note": "Tồn tại ⇒ true."},
        {"input": {"matrix": [[1, 3, 5, 7], [10, 11, 16, 20], [23, 30, 34, 60]],
                   "target": 13}, "note": "Không có ⇒ false."},
        {"input": {"matrix": [[1]], "target": 1}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_matrix2d_case(rng) for _ in range(24)],
)


def _matrix2d_case(rng):
    m, n = rng.randint(1, 4), rng.randint(1, 4)
    flat = sorted(unique_ints(rng, m * n, -20, 40))
    mat = [flat[i * n:(i + 1) * n] for i in range(m)]
    if rng.random() < 0.5:
        target = rng.choice(flat)
    else:
        target = rng.randint(-25, 45)
    return {"matrix": mat, "target": target}


_p(
    title="Number of Islands",
    difficulty="MEDIUM",
    tags=["array", "depth-first-search", "breadth-first-search", "matrix"],
    description=(
        "Given an `m x n` 2D binary `grid` which represents a map of '1's (land) "
        "and '0's (water), return the number of islands. An island is surrounded "
        "by water and is formed by connecting adjacent lands horizontally or "
        "vertically."),
    constraints="- `1 <= m, n <= 300`\n- `grid[i][j]` is '0' or '1'.",
    time="O(m*n)", space="O(m*n)",
    fn="numIslands", params=[("grid", "char[][]")], **{"return": "int"},
    ref="""
class Solution:
    def numIslands(self, grid):
        R, C = len(grid), len(grid[0])
        seen = set()
        def dfs(r, c):
            stack = [(r, c)]
            while stack:
                x, y = stack.pop()
                if 0 <= x < R and 0 <= y < C and (x, y) not in seen and grid[x][y] == "1":
                    seen.add((x, y))
                    stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        cnt = 0
        for r in range(R):
            for c in range(C):
                if grid[r][c] == "1" and (r, c) not in seen:
                    cnt += 1
                    dfs(r, c)
        return cnt
""",
    examples=[
        {"input": {"grid": [["1", "1", "0"], ["1", "0", "0"], ["0", "0", "1"]]},
         "note": "Hai đảo ⇒ 2."},
        {"input": {"grid": [["1", "1"], ["1", "1"]]}, "note": "Một đảo ⇒ 1."},
        {"input": {"grid": [["0"]]}, "note": "Toàn nước ⇒ 0."},
    ],
    gen=lambda rng: [{"grid": grid01(rng, rng.randint(1, 5), rng.randint(1, 5))}
                     for _ in range(24)],
)

_p(
    title="Rotting Oranges",
    difficulty="MEDIUM",
    tags=["array", "breadth-first-search", "matrix"],
    description=(
        "In a grid, each cell is 0 (empty), 1 (fresh orange), or 2 (rotten). Every "
        "minute, any fresh orange adjacent (4-directionally) to a rotten orange "
        "becomes rotten. Return the minimum number of minutes until no fresh "
        "orange remains, or `-1` if impossible."),
    constraints="- `1 <= m, n <= 10`\n- `grid[i][j]` is 0, 1, or 2.",
    time="O(m*n)", space="O(m*n)",
    fn="orangesRotting", params=[("grid", "int[][]")], **{"return": "int"},
    ref="""
class Solution:
    def orangesRotting(self, grid):
        from collections import deque
        R, C = len(grid), len(grid[0])
        q = deque()
        fresh = 0
        for r in range(R):
            for c in range(C):
                if grid[r][c] == 2:
                    q.append((r, c, 0))
                elif grid[r][c] == 1:
                    fresh += 1
        t = 0
        while q:
            r, c, t = q.popleft()
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nr, nc = r + dr, c + dc
                if 0 <= nr < R and 0 <= nc < C and grid[nr][nc] == 1:
                    grid[nr][nc] = 2
                    fresh -= 1
                    q.append((nr, nc, t + 1))
        return t if fresh == 0 else -1
""",
    examples=[
        {"input": {"grid": [[2, 1, 1], [1, 1, 0], [0, 1, 1]]}, "note": "⇒ 4."},
        {"input": {"grid": [[2, 1, 1], [0, 1, 1], [1, 0, 1]]},
         "note": "Có cam cô lập ⇒ -1."},
        {"input": {"grid": [[0, 2]]}, "note": "Không có cam tươi ⇒ 0."},
    ],
    gen=lambda rng: [{"grid": _orange_grid(rng)} for _ in range(24)],
)


def _orange_grid(rng):
    r, c = rng.randint(1, 4), rng.randint(1, 4)
    return [[rng.choice([0, 1, 1, 2]) for _ in range(c)] for _ in range(r)]

_p(
    title="Maximal Square",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming", "matrix"],
    description=(
        "Given an `m x n` binary `matrix` filled with 0's and 1's, find the "
        "largest square containing only 1's and return its area."),
    constraints="- `1 <= m, n <= 300`\n- `matrix[i][j]` is '0' or '1'.",
    time="O(m*n)", space="O(n)",
    fn="maximalSquare", params=[("matrix", "char[][]")], **{"return": "int"},
    ref="""
class Solution:
    def maximalSquare(self, matrix):
        R, C = len(matrix), len(matrix[0])
        dp = [0] * (C + 1)
        best = 0
        prev = 0
        for i in range(R):
            prev = 0
            for j in range(C):
                tmp = dp[j + 1]
                if matrix[i][j] == "1":
                    dp[j + 1] = min(dp[j], dp[j + 1], prev) + 1
                    best = max(best, dp[j + 1])
                else:
                    dp[j + 1] = 0
                prev = tmp
        return best * best
""",
    examples=[
        {"input": {"matrix": [["1", "0", "1", "0", "0"], ["1", "0", "1", "1", "1"],
                              ["1", "1", "1", "1", "1"], ["1", "0", "0", "1", "0"]]},
         "note": "Hình vuông 2x2 ⇒ 4."},
        {"input": {"matrix": [["0", "1"], ["1", "0"]]}, "note": "⇒ 1."},
        {"input": {"matrix": [["0"]]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"matrix": grid01(rng, rng.randint(1, 5), rng.randint(1, 5), 0.6)}
                     for _ in range(24)],
)

# ── dynamic programming ────────────────────────────────────────────────────
_p(
    title="House Robber",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "You are a robber planning to rob houses along a street. Each house has "
        "some money, but adjacent houses have connected security systems. Given an "
        "integer array `nums`, return the maximum amount you can rob tonight "
        "without alerting the police (no two adjacent houses)."),
    constraints="- `1 <= nums.length <= 100`\n- `0 <= nums[i] <= 400`",
    time="O(n)", space="O(1)",
    fn="rob", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def rob(self, nums):
        prev = cur = 0
        for v in nums:
            prev, cur = cur, max(cur, prev + v)
        return cur
""",
    examples=[
        {"input": {"nums": [1, 2, 3, 1]}, "note": "Cướp nhà 1 và 3 ⇒ 4."},
        {"input": {"nums": [2, 7, 9, 3, 1]}, "note": "⇒ 12."},
        {"input": {"nums": [5]}, "note": "⇒ 5."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 12), 0, 30)} for _ in range(24)],
)

_p(
    title="House Robber II",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "All houses at this place are arranged in a circle, so the first and last "
        "houses are adjacent. Given an integer array `nums`, return the maximum "
        "amount you can rob without robbing two adjacent houses."),
    constraints="- `1 <= nums.length <= 100`\n- `0 <= nums[i] <= 1000`",
    time="O(n)", space="O(1)",
    fn="rob", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def rob(self, nums):
        if len(nums) == 1:
            return nums[0]
        def line(a):
            prev = cur = 0
            for v in a:
                prev, cur = cur, max(cur, prev + v)
            return cur
        return max(line(nums[1:]), line(nums[:-1]))
""",
    examples=[
        {"input": {"nums": [2, 3, 2]}, "note": "Đầu cuối kề nhau ⇒ 3."},
        {"input": {"nums": [1, 2, 3, 1]}, "note": "⇒ 4."},
        {"input": {"nums": [0]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 12), 0, 30)} for _ in range(24)],
)

_p(
    title="Longest Increasing Subsequence",
    difficulty="MEDIUM",
    tags=["array", "binary-search", "dynamic-programming"],
    description=(
        "Given an integer array `nums`, return the length of the longest strictly "
        "increasing subsequence."),
    constraints="- `1 <= nums.length <= 2500`\n- `-10^4 <= nums[i] <= 10^4`",
    time="O(n log n)", space="O(n)",
    fn="lengthOfLIS", params=[("nums", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def lengthOfLIS(self, nums):
        import bisect
        tails = []
        for v in nums:
            i = bisect.bisect_left(tails, v)
            if i == len(tails):
                tails.append(v)
            else:
                tails[i] = v
        return len(tails)
""",
    examples=[
        {"input": {"nums": [10, 9, 2, 5, 3, 7, 101, 18]}, "note": "[2,3,7,101] ⇒ 4."},
        {"input": {"nums": [0, 1, 0, 3, 2, 3]}, "note": "⇒ 4."},
        {"input": {"nums": [7, 7, 7]}, "note": "Tăng ngặt ⇒ 1."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 14), -10, 10)} for _ in range(24)],
)

_p(
    title="Coin Change",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming", "breadth-first-search"],
    description=(
        "You are given an integer array `coins` representing coin denominations and "
        "an integer `amount`. Return the fewest number of coins needed to make up "
        "`amount`. If it cannot be made, return `-1`. You may use each coin an "
        "unlimited number of times."),
    constraints="- `1 <= coins.length <= 12`\n- `1 <= coins[i] <= 2^31 - 1`\n- `0 <= amount <= 10^4`",
    time="O(amount * coins)", space="O(amount)",
    fn="coinChange", params=[("coins", "int[]"), ("amount", "int")],
    **{"return": "int"},
    ref="""
class Solution:
    def coinChange(self, coins, amount):
        INF = amount + 1
        dp = [0] + [INF] * amount
        for a in range(1, amount + 1):
            for c in coins:
                if c <= a:
                    dp[a] = min(dp[a], dp[a - c] + 1)
        return dp[amount] if dp[amount] != INF else -1
""",
    examples=[
        {"input": {"coins": [1, 2, 5], "amount": 11}, "note": "5+5+1 ⇒ 3."},
        {"input": {"coins": [2], "amount": 3}, "note": "Không thể ⇒ -1."},
        {"input": {"coins": [1], "amount": 0}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"coins": sorted(set(unique_ints(rng, rng.randint(1, 4), 1, 9))),
                      "amount": rng.randint(0, 25)} for _ in range(24)],
)

_p(
    title="Coin Change II",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "You are given an integer array `coins` and an integer `amount`. Return the "
        "number of combinations that make up that amount. You may assume an "
        "infinite number of each kind of coin."),
    constraints="- `1 <= coins.length <= 300`\n- `1 <= coins[i] <= 5000`\n- `0 <= amount <= 5000`",
    time="O(amount * coins)", space="O(amount)",
    fn="change", params=[("amount", "int"), ("coins", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def change(self, amount, coins):
        dp = [1] + [0] * amount
        for c in coins:
            for a in range(c, amount + 1):
                dp[a] += dp[a - c]
        return dp[amount]
""",
    examples=[
        {"input": {"amount": 5, "coins": [1, 2, 5]}, "note": "4 cách ⇒ 4."},
        {"input": {"amount": 3, "coins": [2]}, "note": "Không thể ⇒ 0."},
        {"input": {"amount": 0, "coins": [7]}, "note": "Một cách (rỗng) ⇒ 1."},
    ],
    gen=lambda rng: [{"amount": rng.randint(0, 20),
                      "coins": sorted(set(unique_ints(rng, rng.randint(1, 4), 1, 8)))}
                     for _ in range(24)],
)

_p(
    title="Min Cost Climbing Stairs",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "You are given an integer array `cost` where `cost[i]` is the cost of the "
        "i-th step. You can climb one or two steps. You can start from step 0 or "
        "step 1. Return the minimum cost to reach the top of the floor (just past "
        "the last index)."),
    constraints="- `2 <= cost.length <= 1000`\n- `0 <= cost[i] <= 999`",
    time="O(n)", space="O(1)",
    fn="minCostClimbingStairs", params=[("cost", "int[]")], **{"return": "int"},
    ref="""
class Solution:
    def minCostClimbingStairs(self, cost):
        a = b = 0
        for i in range(2, len(cost) + 1):
            a, b = b, min(b + cost[i - 1], a + cost[i - 2])
        return b
""",
    examples=[
        {"input": {"cost": [10, 15, 20]}, "note": "Bắt đầu từ bước 1 ⇒ 15."},
        {"input": {"cost": [1, 100, 1, 1, 1, 100, 1, 1, 100, 1]}, "note": "⇒ 6."},
        {"input": {"cost": [0, 0]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [{"cost": ints(rng, rng.randint(2, 12), 0, 30)} for _ in range(24)],
)

_p(
    title="Decode Ways",
    difficulty="MEDIUM",
    tags=["string", "dynamic-programming"],
    description=(
        "A message containing letters A-Z is encoded by mapping 'A'->\"1\" ... "
        "'Z'->\"26\". Given a string `s` containing only digits, return the number "
        "of ways to decode it. A leading zero or an invalid group makes that "
        "decoding impossible."),
    constraints="- `1 <= s.length <= 100`\n- `s` contains only digits.",
    time="O(n)", space="O(1)",
    fn="numDecodings", params=[("s", "string")], **{"return": "int"},
    ref="""
class Solution:
    def numDecodings(self, s):
        if not s or s[0] == "0":
            return 0
        prev, cur = 1, 1
        for i in range(1, len(s)):
            tmp = 0
            if s[i] != "0":
                tmp += cur
            if 10 <= int(s[i - 1:i + 1]) <= 26:
                tmp += prev
            prev, cur = cur, tmp
        return cur
""",
    examples=[
        {"input": {"s": "12"}, "note": "\"AB\" hoặc \"L\" ⇒ 2."},
        {"input": {"s": "226"}, "note": "⇒ 3."},
        {"input": {"s": "06"}, "note": "Dẫn đầu bằng 0 ⇒ 0."},
    ],
    gen=lambda rng: [{"s": "".join(str(rng.randint(0, 9))
                                   for _ in range(rng.randint(1, 7)))} for _ in range(24)],
)

_p(
    title="Unique Paths",
    difficulty="MEDIUM",
    tags=["math", "dynamic-programming", "combinatorics"],
    description=(
        "A robot is located at the top-left corner of an `m x n` grid. It can only "
        "move down or right. How many possible unique paths are there to reach the "
        "bottom-right corner?"),
    constraints="- `1 <= m, n <= 100`",
    time="O(m*n)", space="O(n)",
    fn="uniquePaths", params=[("m", "int"), ("n", "int")], **{"return": "int"},
    ref="""
class Solution:
    def uniquePaths(self, m, n):
        dp = [1] * n
        for _ in range(1, m):
            for j in range(1, n):
                dp[j] += dp[j - 1]
        return dp[-1]
""",
    examples=[
        {"input": {"m": 3, "n": 7}, "note": "⇒ 28."},
        {"input": {"m": 3, "n": 2}, "note": "⇒ 3."},
        {"input": {"m": 1, "n": 1}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [{"m": rng.randint(1, 10), "n": rng.randint(1, 10)} for _ in range(44)],
)

_p(
    title="Unique Paths II",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming", "matrix"],
    description=(
        "You are given an `m x n` integer grid `obstacleGrid` where `1` marks an "
        "obstacle and `0` is empty. A robot starts at the top-left and can move "
        "only down or right. Return the number of unique paths to the "
        "bottom-right. If the start or end is blocked, return 0."),
    constraints="- `1 <= m, n <= 100`\n- `obstacleGrid[i][j]` is 0 or 1.",
    time="O(m*n)", space="O(n)",
    fn="uniquePathsWithObstacles", params=[("obstacleGrid", "int[][]")],
    **{"return": "int"},
    ref="""
class Solution:
    def uniquePathsWithObstacles(self, obstacleGrid):
        g = obstacleGrid
        n = len(g[0])
        dp = [0] * n
        dp[0] = 1
        for row in g:
            for j in range(n):
                if row[j] == 1:
                    dp[j] = 0
                elif j > 0:
                    dp[j] += dp[j - 1]
        return dp[-1]
""",
    examples=[
        {"input": {"obstacleGrid": [[0, 0, 0], [0, 1, 0], [0, 0, 0]]}, "note": "⇒ 2."},
        {"input": {"obstacleGrid": [[0, 1], [0, 0]]}, "note": "⇒ 1."},
        {"input": {"obstacleGrid": [[1]]}, "note": "Bị chặn ngay ⇒ 0."},
    ],
    gen=lambda rng: [{"obstacleGrid": _obstacle_grid(rng)} for _ in range(24)],
)


def _obstacle_grid(rng):
    r, c = rng.randint(1, 4), rng.randint(1, 4)
    return [[rng.choice([0, 0, 0, 1]) for _ in range(c)] for _ in range(r)]

_p(
    title="Minimum Path Sum",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming", "matrix"],
    description=(
        "Given an `m x n` `grid` filled with non-negative numbers, find a path "
        "from top-left to bottom-right which minimizes the sum of all numbers "
        "along its path. You can only move down or right."),
    constraints="- `1 <= m, n <= 200`\n- `0 <= grid[i][j] <= 200`",
    time="O(m*n)", space="O(n)",
    fn="minPathSum", params=[("grid", "int[][]")], **{"return": "int"},
    ref="""
class Solution:
    def minPathSum(self, grid):
        m, n = len(grid), len(grid[0])
        dp = [float("inf")] * n
        dp[0] = 0
        for i in range(m):
            dp[0] += grid[i][0]
            for j in range(1, n):
                dp[j] = min(dp[j], dp[j - 1]) + grid[i][j]
        return dp[-1]
""",
    examples=[
        {"input": {"grid": [[1, 3, 1], [1, 5, 1], [4, 2, 1]]},
         "note": "1→3→1→1→1 ⇒ 7."},
        {"input": {"grid": [[1, 2, 3], [4, 5, 6]]}, "note": "⇒ 12."},
        {"input": {"grid": [[5]]}, "note": "⇒ 5."},
    ],
    gen=lambda rng: [{"grid": matrix(rng, rng.randint(1, 5), rng.randint(1, 5), 0, 9)}
                     for _ in range(24)],
)

_p(
    title="Partition Equal Subset Sum",
    difficulty="MEDIUM",
    tags=["array", "dynamic-programming"],
    description=(
        "Given an integer array `nums`, return `true` if you can partition the "
        "array into two subsets such that the sum of the elements in both subsets "
        "is equal."),
    constraints="- `1 <= nums.length <= 200`\n- `1 <= nums[i] <= 100`",
    time="O(n * sum)", space="O(sum)",
    fn="canPartition", params=[("nums", "int[]")], **{"return": "boolean"},
    ref="""
class Solution:
    def canPartition(self, nums):
        total = sum(nums)
        if total % 2:
            return False
        target = total // 2
        dp = 1
        for v in nums:
            dp |= dp << v
        return (dp >> target) & 1 == 1
""",
    examples=[
        {"input": {"nums": [1, 5, 11, 5]}, "note": "[1,5,5] và [11] ⇒ true."},
        {"input": {"nums": [1, 2, 3, 5]}, "note": "Không chia được ⇒ false."},
        {"input": {"nums": [2, 2]}, "note": "⇒ true."},
    ],
    gen=lambda rng: [{"nums": ints(rng, rng.randint(1, 10), 1, 12)} for _ in range(24)],
)

_p(
    title="Word Break",
    difficulty="MEDIUM",
    tags=["hash-table", "string", "dynamic-programming", "trie"],
    description=(
        "Given a string `s` and a dictionary of strings `wordDict`, return `true` "
        "if `s` can be segmented into a space-separated sequence of one or more "
        "dictionary words. The same word may be reused multiple times."),
    constraints="- `1 <= s.length <= 300`\n- `1 <= wordDict.length <= 1000`\n- Lowercase English letters.",
    time="O(n^2)", space="O(n)",
    fn="wordBreak", params=[("s", "string"), ("wordDict", "string[]")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def wordBreak(self, s, wordDict):
        words = set(wordDict)
        n = len(s)
        dp = [False] * (n + 1)
        dp[0] = True
        for i in range(1, n + 1):
            for j in range(i):
                if dp[j] and s[j:i] in words:
                    dp[i] = True
                    break
        return dp[n]
""",
    examples=[
        {"input": {"s": "leetcode", "wordDict": ["leet", "code"]},
         "note": "\"leet\"+\"code\" ⇒ true."},
        {"input": {"s": "applepenapple", "wordDict": ["apple", "pen"]},
         "note": "⇒ true."},
        {"input": {"s": "catsandog",
                   "wordDict": ["cats", "dog", "sand", "and", "cat"]},
         "note": "Không ghép hết ⇒ false."},
    ],
    gen=lambda rng: [_wordbreak_case(rng) for _ in range(24)],
)


def _wordbreak_case(rng):
    pieces = [lower(rng, rng.randint(1, 3)) for _ in range(rng.randint(2, 4))]
    s = "".join(rng.choice(pieces) for _ in range(rng.randint(1, 4)))
    extra = [lower(rng, rng.randint(1, 3)) for _ in range(rng.randint(0, 2))]
    dic = list(dict.fromkeys(pieces + extra))
    rng.shuffle(dic)
    return {"s": s, "wordDict": dic}


# ── backtracking (canonical output) ────────────────────────────────────────
_p(
    title="Combination Sum",
    difficulty="MEDIUM",
    tags=["array", "backtracking"],
    description=(
        "Given an array of distinct integers `candidates` and a target integer "
        "`target`, return all unique combinations of `candidates` where the chosen "
        "numbers sum to `target`. The same number may be chosen unlimited times. "
        "Each combination must be listed in non-decreasing order."),
    constraints="- `1 <= candidates.length <= 30`\n- `2 <= candidates[i] <= 40`\n- All elements are distinct.",
    time="O(2^t)", space="O(t)",
    fn="combinationSum", params=[("candidates", "int[]"), ("target", "int")],
    **{"return": "List<List<Integer>>"}, orderMatters=False,
    ref="""
class Solution:
    def combinationSum(self, candidates, target):
        candidates = sorted(candidates)
        res = []
        def bt(start, remain, path):
            if remain == 0:
                res.append(path[:])
                return
            for i in range(start, len(candidates)):
                c = candidates[i]
                if c > remain:
                    break
                path.append(c)
                bt(i, remain - c, path)
                path.pop()
        bt(0, target, [])
        return res
""",
    examples=[
        {"input": {"candidates": [2, 3, 6, 7], "target": 7},
         "note": "[2,2,3] và [7] ⇒ [[2,2,3],[7]]."},
        {"input": {"candidates": [2, 3, 5], "target": 8},
         "note": "⇒ [[2,2,2,2],[2,3,3],[3,5]]."},
        {"input": {"candidates": [2], "target": 1}, "note": "Không thể ⇒ []."},
    ],
    gen=lambda rng: [{"candidates": sorted(set(unique_ints(rng, rng.randint(1, 4), 2, 7))),
                      "target": rng.randint(1, 12)} for _ in range(24)],
)

_p(
    title="Letter Combinations of a Phone Number",
    difficulty="MEDIUM",
    tags=["hash-table", "string", "backtracking"],
    description=(
        "Given a string containing digits from 2-9, return all possible letter "
        "combinations that the number could represent (phone keypad mapping). "
        "Return the answer in any order. If the input is empty, return an empty "
        "list."),
    constraints="- `0 <= digits.length <= 4`\n- `digits[i]` is in the range `['2', '9']`.",
    time="O(4^n)", space="O(n)",
    fn="letterCombinations", params=[("digits", "string")],
    **{"return": "List<String>"}, orderMatters=False,
    ref="""
class Solution:
    def letterCombinations(self, digits):
        if not digits:
            return []
        m = {"2":"abc","3":"def","4":"ghi","5":"jkl",
             "6":"mno","7":"pqrs","8":"tuv","9":"wxyz"}
        res = [""]
        for d in digits:
            res = [p + c for p in res for c in m[d]]
        return res
""",
    examples=[
        {"input": {"digits": "23"},
         "note": "⇒ [\"ad\",\"ae\",\"af\",\"bd\",\"be\",\"bf\",\"cd\",\"ce\",\"cf\"]."},
        {"input": {"digits": ""}, "note": "Rỗng ⇒ []."},
        {"input": {"digits": "2"}, "note": "⇒ [\"a\",\"b\",\"c\"]."},
    ],
    gen=lambda rng: ([{"digits": ""}]
                     + [{"digits": "".join(str(rng.randint(2, 9))
                                           for _ in range(rng.randint(1, 3)))}
                        for _ in range(23)]),
)

_p(
    title="Word Search",
    difficulty="MEDIUM",
    tags=["array", "backtracking", "matrix"],
    description=(
        "Given an `m x n` grid of characters `board` and a string `word`, return "
        "`true` if `word` exists in the grid. The word can be constructed from "
        "letters of sequentially adjacent cells (horizontally or vertically); the "
        "same cell may not be used more than once."),
    constraints="- `1 <= m, n <= 6`\n- `1 <= word.length <= 15`\n- Lowercase English letters.",
    time="O(m*n*4^L)", space="O(L)",
    fn="exist", params=[("board", "char[][]"), ("word", "string")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def exist(self, board, word):
        R, C = len(board), len(board[0])
        def dfs(r, c, i):
            if i == len(word):
                return True
            if r < 0 or r >= R or c < 0 or c >= C or board[r][c] != word[i]:
                return False
            tmp = board[r][c]
            board[r][c] = "#"
            found = (dfs(r + 1, c, i + 1) or dfs(r - 1, c, i + 1)
                     or dfs(r, c + 1, i + 1) or dfs(r, c - 1, i + 1))
            board[r][c] = tmp
            return found
        for r in range(R):
            for c in range(C):
                if dfs(r, c, 0):
                    return True
        return False
""",
    examples=[
        {"input": {"board": [["a", "b", "c", "e"], ["s", "f", "c", "s"],
                             ["a", "d", "e", "e"]], "word": "abcced"},
         "note": "Tồn tại ⇒ true."},
        {"input": {"board": [["a", "b"], ["c", "d"]], "word": "abcd"},
         "note": "Không liền kề được ⇒ false."},
        {"input": {"board": [["a"]], "word": "a"}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_wordsearch_case(rng) for _ in range(24)],
)


def _wordsearch_case(rng):
    R, C = rng.randint(1, 3), rng.randint(1, 3)
    board = [[rng.choice("abc") for _ in range(C)] for _ in range(R)]
    if rng.random() < 0.5:
        # walk a real path to embed a guaranteed word sometimes
        r, c = rng.randint(0, R - 1), rng.randint(0, C - 1)
        seen = {(r, c)}
        word = board[r][c]
        for _ in range(rng.randint(0, 3)):
            opts = [(r + dr, c + dc) for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if 0 <= r + dr < R and 0 <= c + dc < C and (r + dr, c + dc) not in seen]
            if not opts:
                break
            r, c = rng.choice(opts)
            seen.add((r, c))
            word += board[r][c]
        return {"board": board, "word": word}
    return {"board": board, "word": "".join(rng.choice("abcd") for _ in range(rng.randint(1, 4)))}


# ── graphs ─────────────────────────────────────────────────────────────────
_p(
    title="Course Schedule",
    difficulty="MEDIUM",
    tags=["depth-first-search", "breadth-first-search", "graph", "topological-sort"],
    description=(
        "There are `numCourses` courses labeled `0` to `numCourses - 1`. Given "
        "`prerequisites` where `prerequisites[i] = [a, b]` means you must take "
        "course `b` before course `a`, return `true` if you can finish all "
        "courses."),
    constraints="- `1 <= numCourses <= 2000`\n- `0 <= prerequisites.length <= 5000`",
    time="O(V + E)", space="O(V + E)",
    fn="canFinish", params=[("numCourses", "int"), ("prerequisites", "int[][]")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def canFinish(self, numCourses, prerequisites):
        from collections import deque
        adj = [[] for _ in range(numCourses)]
        indeg = [0] * numCourses
        for a, b in prerequisites:
            adj[b].append(a)
            indeg[a] += 1
        q = deque(i for i in range(numCourses) if indeg[i] == 0)
        done = 0
        while q:
            u = q.popleft()
            done += 1
            for v in adj[u]:
                indeg[v] -= 1
                if indeg[v] == 0:
                    q.append(v)
        return done == numCourses
""",
    examples=[
        {"input": {"numCourses": 2, "prerequisites": [[1, 0]]},
         "note": "0→1 hợp lệ ⇒ true."},
        {"input": {"numCourses": 2, "prerequisites": [[1, 0], [0, 1]]},
         "note": "Có chu trình ⇒ false."},
        {"input": {"numCourses": 1, "prerequisites": []}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_course_case(rng) for _ in range(34)],
)


def _course_case(rng):
    n = rng.randint(1, 6)
    edges = []
    for _ in range(rng.randint(0, n + 1)):
        a, b = rng.randint(0, n - 1), rng.randint(0, n - 1)
        if a != b:
            edges.append([a, b])
    return {"numCourses": n, "prerequisites": edges}


_p(
    title="Number of Connected Components in an Undirected Graph",
    difficulty="MEDIUM",
    tags=["depth-first-search", "breadth-first-search", "union-find", "graph"],
    description=(
        "You have a graph of `n` nodes labeled `0` to `n - 1`. Given `n` and an "
        "array `edges` where `edges[i] = [a, b]` is an undirected edge, return the "
        "number of connected components in the graph."),
    constraints="- `1 <= n <= 2000`\n- `0 <= edges.length <= 5000`",
    time="O(V + E)", space="O(V)",
    fn="countComponents", params=[("n", "int"), ("edges", "int[][]")],
    **{"return": "int"},
    ref="""
class Solution:
    def countComponents(self, n, edges):
        parent = list(range(n))
        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        comp = n
        for a, b in edges:
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[ra] = rb
                comp -= 1
        return comp
""",
    examples=[
        {"input": {"n": 5, "edges": [[0, 1], [1, 2], [3, 4]]},
         "note": "{0,1,2} và {3,4} ⇒ 2."},
        {"input": {"n": 5, "edges": [[0, 1], [1, 2], [2, 3], [3, 4]]},
         "note": "Một chuỗi ⇒ 1."},
        {"input": {"n": 3, "edges": []}, "note": "Rời rạc ⇒ 3."},
    ],
    gen=lambda rng: [_components_case(rng) for _ in range(34)],
)


def _components_case(rng):
    n = rng.randint(1, 7)
    edges = []
    for _ in range(rng.randint(0, n)):
        a, b = rng.randint(0, n - 1), rng.randint(0, n - 1)
        if a != b:
            edges.append([a, b])
    return {"n": n, "edges": edges}


# ── greedy ─────────────────────────────────────────────────────────────────
_p(
    title="Gas Station",
    difficulty="MEDIUM",
    tags=["array", "greedy"],
    description=(
        "There are `n` gas stations on a circular route. `gas[i]` is the fuel at "
        "station `i`, and `cost[i]` is the fuel needed to travel from station `i` "
        "to `i+1`. Return the starting station's index if you can travel around "
        "the circuit once in the clockwise direction, otherwise return `-1`. If a "
        "solution exists, it is guaranteed to be unique."),
    constraints="- `n == gas.length == cost.length`\n- `1 <= n <= 10^5`",
    time="O(n)", space="O(1)",
    fn="canCompleteCircuit", params=[("gas", "int[]"), ("cost", "int[]")],
    **{"return": "int"},
    ref="""
class Solution:
    def canCompleteCircuit(self, gas, cost):
        if sum(gas) < sum(cost):
            return -1
        total = 0
        start = 0
        for i in range(len(gas)):
            total += gas[i] - cost[i]
            if total < 0:
                start = i + 1
                total = 0
        return start
""",
    examples=[
        {"input": {"gas": [1, 2, 3, 4, 5], "cost": [3, 4, 5, 1, 2]},
         "note": "Bắt đầu tại 3 ⇒ 3."},
        {"input": {"gas": [2, 3, 4], "cost": [3, 4, 3]}, "note": "Không thể ⇒ -1."},
        {"input": {"gas": [5], "cost": [4]}, "note": "⇒ 0."},
    ],
    gen=lambda rng: [_gas_case(rng) for _ in range(24)],
)


def _gas_case(rng):
    n = rng.randint(1, 8)
    gas = ints(rng, n, 0, 8)
    cost = ints(rng, n, 0, 8)
    return {"gas": gas, "cost": cost}


_p(
    title="Hand of Straights",
    difficulty="MEDIUM",
    tags=["array", "hash-table", "greedy", "sorting"],
    description=(
        "Given an integer array `hand` of card values and an integer `groupSize`, "
        "return `true` if the cards can be rearranged into groups of `groupSize` "
        "consecutive cards."),
    constraints="- `1 <= hand.length <= 10^4`\n- `1 <= groupSize <= hand.length`",
    time="O(n log n)", space="O(n)",
    fn="isNStraightHand", params=[("hand", "int[]"), ("groupSize", "int")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def isNStraightHand(self, hand, groupSize):
        from collections import Counter
        if len(hand) % groupSize:
            return False
        cnt = Counter(hand)
        for v in sorted(cnt):
            c = cnt[v]
            if c > 0:
                for k in range(v, v + groupSize):
                    if cnt[k] < c:
                        return False
                    cnt[k] -= c
        return True
""",
    examples=[
        {"input": {"hand": [1, 2, 3, 6, 2, 3, 4, 7, 8], "groupSize": 3},
         "note": "[1,2,3],[2,3,4],[6,7,8] ⇒ true."},
        {"input": {"hand": [1, 2, 3, 4, 5], "groupSize": 4},
         "note": "Không chia hết ⇒ false."},
        {"input": {"hand": [1, 1, 2, 2, 3, 3], "groupSize": 3}, "note": "⇒ true."},
    ],
    gen=lambda rng: [{"hand": ints(rng, rng.randint(1, 10), 1, 8),
                      "groupSize": rng.randint(1, 4)} for _ in range(24)],
)

_p(
    title="Evaluate Reverse Polish Notation",
    difficulty="MEDIUM",
    tags=["array", "math", "stack"],
    description=(
        "You are given an array of strings `tokens` representing an arithmetic "
        "expression in Reverse Polish Notation. Evaluate the expression and return "
        "the integer result. Division truncates toward zero. Valid operators are "
        "+, -, *, /."),
    constraints="- `1 <= tokens.length <= 10^4`\n- Each token is an operator or an integer.",
    time="O(n)", space="O(n)",
    fn="evalRPN", params=[("tokens", "string[]")], **{"return": "int"},
    ref="""
class Solution:
    def evalRPN(self, tokens):
        st = []
        for t in tokens:
            if t in ("+", "-", "*", "/"):
                b = st.pop(); a = st.pop()
                if t == "+": st.append(a + b)
                elif t == "-": st.append(a - b)
                elif t == "*": st.append(a * b)
                else: st.append(int(a / b))
            else:
                st.append(int(t))
        return st[0]
""",
    examples=[
        {"input": {"tokens": ["2", "1", "+", "3", "*"]}, "note": "(2+1)*3 ⇒ 9."},
        {"input": {"tokens": ["4", "13", "5", "/", "+"]}, "note": "4+(13/5) ⇒ 6."},
        {"input": {"tokens": ["5"]}, "note": "⇒ 5."},
    ],
    gen=lambda rng: [{"tokens": _rpn(rng)} for _ in range(24)],
)


def _rpn(rng):
    # build a valid RPN expression that never divides by zero
    stack_size = 0
    out = []
    steps = rng.randint(1, 6)
    for _ in range(steps):
        if stack_size >= 2 and rng.random() < 0.5:
            op = rng.choice(["+", "-", "*"])
            out.append(op)
            stack_size -= 1
        else:
            out.append(str(rng.randint(1, 9)))
            stack_size += 1
    while stack_size > 1:
        out.append(rng.choice(["+", "-", "*"]))
        stack_size -= 1
    return out


# ── trees (medium) ─────────────────────────────────────────────────────────
_p(
    title="Validate Binary Search Tree",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "binary-search-tree", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, determine if it is a valid binary "
        "search tree (BST): every node's left subtree contains only smaller keys, "
        "the right subtree only larger keys, and both subtrees are valid BSTs."),
    constraints="- The number of nodes is in the range `[1, 10^4]`.",
    time="O(n)", space="O(h)",
    fn="isValidBST", params=[("root", "TreeNode")], **{"return": "boolean"},
    ref="""
class Solution:
    def isValidBST(self, root):
        def ok(node, lo, hi):
            if not node:
                return True
            if not (lo < node.val < hi):
                return False
            return ok(node.left, lo, node.val) and ok(node.right, node.val, hi)
        return ok(root, float("-inf"), float("inf"))
""",
    examples=[
        {"input": {"root": [2, 1, 3]}, "note": "BST hợp lệ ⇒ true."},
        {"input": {"root": [5, 1, 4, None, None, 3, 6]},
         "note": "4 nằm sai phía ⇒ false."},
        {"input": {"root": [1]}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_bst_validate_case(rng) for _ in range(24)],
)


def _bst_validate_case(rng):
    if rng.random() < 0.5:
        return {"root": bst_array(rng, rng.randint(1, 12), 0, 40) or [rng.randint(0, 40)]}
    return {"root": tree_array(rng, rng.randint(1, 10), 0, 20)}


_p(
    title="Kth Smallest Element in a BST",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "binary-search-tree", "binary-tree"],
    description=(
        "Given the `root` of a binary search tree and an integer `k`, return the "
        "k-th smallest value (1-indexed) of all the node values in the tree."),
    constraints="- The number of nodes is `n`, with `1 <= k <= n <= 10^4`.",
    time="O(h + k)", space="O(h)",
    fn="kthSmallest", params=[("root", "TreeNode"), ("k", "int")], **{"return": "int"},
    ref="""
class Solution:
    def kthSmallest(self, root, k):
        stack, cur = [], root
        while cur or stack:
            while cur:
                stack.append(cur); cur = cur.left
            cur = stack.pop()
            k -= 1
            if k == 0:
                return cur.val
            cur = cur.right
""",
    examples=[
        {"input": {"root": [3, 1, 4, None, 2], "k": 1}, "note": "Nhỏ nhất ⇒ 1."},
        {"input": {"root": [5, 3, 6, 2, 4, None, None, 1], "k": 3}, "note": "⇒ 3."},
        {"input": {"root": [1], "k": 1}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [_kth_bst_case(rng) for _ in range(24)],
)


def _kth_bst_case(rng):
    n = rng.randint(1, 12)
    arr = bst_array(rng, n, 0, 60) or [rng.randint(0, 60)]
    size = sum(1 for x in arr if x is not None)
    return {"root": arr, "k": rng.randint(1, size)}


_p(
    title="Binary Tree Level Order Traversal",
    difficulty="MEDIUM",
    tags=["tree", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, return the level order traversal of "
        "its nodes' values (i.e., from left to right, level by level)."),
    constraints="- The number of nodes is in the range `[0, 2000]`.",
    time="O(n)", space="O(n)",
    fn="levelOrder", params=[("root", "TreeNode")],
    **{"return": "List<List<Integer>>"}, orderMatters=True,
    ref="""
class Solution:
    def levelOrder(self, root):
        from collections import deque
        if not root:
            return []
        res, q = [], deque([root])
        while q:
            level = []
            for _ in range(len(q)):
                n = q.popleft()
                level.append(n.val)
                if n.left: q.append(n.left)
                if n.right: q.append(n.right)
            res.append(level)
        return res
""",
    examples=[
        {"input": {"root": [3, 9, 20, None, None, 15, 7]},
         "note": "⇒ [[3],[9,20],[15,7]]."},
        {"input": {"root": [1]}, "note": "⇒ [[1]]."},
        {"input": {"root": []}, "note": "⇒ []."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 16), 0, 30)}
                        for _ in range(22)]),
)

_p(
    title="Binary Tree Right Side View",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, imagine yourself standing on the right "
        "side of it. Return the values of the nodes you can see ordered from top "
        "to bottom."),
    constraints="- The number of nodes is in the range `[0, 100]`.",
    time="O(n)", space="O(n)",
    fn="rightSideView", params=[("root", "TreeNode")], **{"return": "List<Integer>"},
    orderMatters=True,
    ref="""
class Solution:
    def rightSideView(self, root):
        from collections import deque
        if not root:
            return []
        res, q = [], deque([root])
        while q:
            n = None
            for _ in range(len(q)):
                n = q.popleft()
                if n.left: q.append(n.left)
                if n.right: q.append(n.right)
            res.append(n.val)
        return res
""",
    examples=[
        {"input": {"root": [1, 2, 3, None, 5, None, 4]}, "note": "⇒ [1,3,4]."},
        {"input": {"root": [1, None, 3]}, "note": "⇒ [1,3]."},
        {"input": {"root": []}, "note": "⇒ []."},
    ],
    gen=lambda rng: ([{"root": []}, {"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 16), 0, 30)}
                        for _ in range(22)]),
)

_p(
    title="Count Good Nodes in Binary Tree",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "breadth-first-search", "binary-tree"],
    description=(
        "Given the `root` of a binary tree, a node X is 'good' if on the path from "
        "the root to X there are no nodes with a value greater than X. Return the "
        "number of good nodes."),
    constraints="- The number of nodes is in the range `[1, 10^5]`.",
    time="O(n)", space="O(h)",
    fn="goodNodes", params=[("root", "TreeNode")], **{"return": "int"},
    ref="""
class Solution:
    def goodNodes(self, root):
        def dfs(node, mx):
            if not node:
                return 0
            good = 1 if node.val >= mx else 0
            mx = max(mx, node.val)
            return good + dfs(node.left, mx) + dfs(node.right, mx)
        return dfs(root, float("-inf"))
""",
    examples=[
        {"input": {"root": [3, 1, 4, 3, None, 1, 5]}, "note": "⇒ 4."},
        {"input": {"root": [3, 3, None, 4, 2]}, "note": "⇒ 3."},
        {"input": {"root": [1]}, "note": "⇒ 1."},
    ],
    gen=lambda rng: ([{"root": [1]}]
                     + [{"root": tree_array(rng, rng.randint(1, 16), 0, 20)}
                        for _ in range(23)]),
)

_p(
    title="Lowest Common Ancestor of a Binary Search Tree",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "binary-search-tree", "binary-tree"],
    description=(
        "Given a binary search tree (BST), find the lowest common ancestor (LCA) "
        "of two given node values `p` and `q`. The LCA is the lowest node that has "
        "both `p` and `q` as descendants (a node can be a descendant of itself). "
        "Both values are guaranteed to exist in the tree."),
    constraints="- The number of nodes is in the range `[2, 10^5]`.\n- `p != q` and both exist.",
    time="O(h)", space="O(1)",
    fn="lowestCommonAncestor",
    params=[("root", "TreeNode"), ("p", "int"), ("q", "int")],
    **{"return": "TreeNode"},
    ref="""
class Solution:
    def lowestCommonAncestor(self, root, p, q):
        cur = root
        while cur:
            if p < cur.val and q < cur.val:
                cur = cur.left
            elif p > cur.val and q > cur.val:
                cur = cur.right
            else:
                return cur
""",
    examples=[
        {"input": {"root": [6, 2, 8, 0, 4, 7, 9, None, None, 3, 5], "p": 2, "q": 8},
         "note": "LCA của 2 và 8 là gốc 6 ⇒ subtree gốc 6."},
        {"input": {"root": [6, 2, 8, 0, 4, 7, 9, None, None, 3, 5], "p": 2, "q": 4},
         "note": "⇒ subtree gốc 2."},
        {"input": {"root": [2, 1], "p": 2, "q": 1}, "note": "⇒ subtree gốc 2."},
    ],
    gen=lambda rng: [_lca_case(rng) for _ in range(24)],
)


def _lca_case(rng):
    n = rng.randint(2, 14)
    arr = bst_array(rng, n, 0, 80)
    vals = [x for x in arr if x is not None]
    while len(vals) < 2:
        n2 = rng.randint(2, 14)
        arr = bst_array(rng, n2, 0, 80)
        vals = [x for x in arr if x is not None]
    p, q = rng.sample(vals, 2)
    return {"root": arr, "p": p, "q": q}


_p(
    title="Construct Binary Tree from Preorder and Inorder Traversal",
    difficulty="MEDIUM",
    tags=["array", "hash-table", "divide-and-conquer", "tree", "binary-tree"],
    description=(
        "Given two integer arrays `preorder` and `inorder` where `preorder` is the "
        "preorder traversal of a binary tree and `inorder` is the inorder "
        "traversal of the same tree, construct and return the binary tree. All "
        "values are unique."),
    constraints="- `1 <= preorder.length <= 3000`\n- `inorder.length == preorder.length`\n- All values are unique.",
    time="O(n)", space="O(n)",
    fn="buildTree", params=[("preorder", "int[]"), ("inorder", "int[]")],
    **{"return": "TreeNode"},
    ref="""
class Solution:
    def buildTree(self, preorder, inorder):
        idx = {v: i for i, v in enumerate(inorder)}
        self.pre = 0
        def build(lo, hi):
            if lo > hi:
                return None
            val = preorder[self.pre]
            self.pre += 1
            node = TreeNode(val)
            mid = idx[val]
            node.left = build(lo, mid - 1)
            node.right = build(mid + 1, hi)
            return node
        return build(0, len(inorder) - 1)
""",
    examples=[
        {"input": {"preorder": [3, 9, 20, 15, 7], "inorder": [9, 3, 15, 20, 7]},
         "note": "⇒ [3,9,20,null,null,15,7]."},
        {"input": {"preorder": [-1], "inorder": [-1]}, "note": "⇒ [-1]."},
        {"input": {"preorder": [1, 2], "inorder": [2, 1]}, "note": "⇒ [1,2]."},
    ],
    gen=lambda rng: [_build_tree_case(rng) for _ in range(24)],
)


def _build_tree_case(rng):
    n = rng.randint(1, 12)
    vals = unique_ints(rng, n, -20, 40)

    def build(a):
        if not a:
            return None
        m = rng.randint(0, len(a) - 1)
        return (a[m], build(a[:m]), build(a[m + 1:]))

    root = build(vals)
    pre, ino = [], []

    def pre_dfs(t):
        if t:
            pre.append(t[0]); pre_dfs(t[1]); pre_dfs(t[2])

    def in_dfs(t):
        if t:
            in_dfs(t[1]); ino.append(t[0]); in_dfs(t[2])

    pre_dfs(root); in_dfs(root)
    return {"preorder": pre, "inorder": ino}


_p(
    title="Subtree of Another Tree",
    difficulty="MEDIUM",
    tags=["tree", "depth-first-search", "string-matching", "binary-tree"],
    description=(
        "Given the roots of two binary trees `root` and `subRoot`, return `true` if "
        "there is a subtree of `root` with the same structure and node values as "
        "`subRoot`."),
    constraints="- Nodes in `root` are in `[1, 2000]`, in `subRoot` in `[1, 1000]`.",
    time="O(m*n)", space="O(h)",
    fn="isSubtree", params=[("root", "TreeNode"), ("subRoot", "TreeNode")],
    **{"return": "boolean"},
    ref="""
class Solution:
    def isSubtree(self, root, subRoot):
        def same(a, b):
            if not a and not b:
                return True
            if not a or not b or a.val != b.val:
                return False
            return same(a.left, b.left) and same(a.right, b.right)
        def dfs(node):
            if not node:
                return False
            return same(node, subRoot) or dfs(node.left) or dfs(node.right)
        return dfs(root)
""",
    examples=[
        {"input": {"root": [3, 4, 5, 1, 2], "subRoot": [4, 1, 2]}, "note": "⇒ true."},
        {"input": {"root": [3, 4, 5, 1, 2, None, None, None, None, 0],
                   "subRoot": [4, 1, 2]}, "note": "Cấu trúc khác ⇒ false."},
        {"input": {"root": [1], "subRoot": [1]}, "note": "⇒ true."},
    ],
    gen=lambda rng: [_subtree_case(rng) for _ in range(24)],
)


def _subtree_case(rng):
    root = tree_array(rng, rng.randint(1, 12), 0, 5) or [rng.randint(0, 5)]
    if rng.random() < 0.5:
        sub = tree_array(rng, rng.randint(1, 4), 0, 5) or [rng.randint(0, 5)]
    else:
        sub = root[:]  # whole tree is its own subtree
    return {"root": root, "subRoot": sub}


# ── linked lists (medium) ──────────────────────────────────────────────────
_p(
    title="Add Two Numbers",
    difficulty="MEDIUM",
    tags=["linked-list", "math", "recursion"],
    description=(
        "You are given two non-empty linked lists representing two non-negative "
        "integers. The digits are stored in reverse order, each node containing a "
        "single digit. Add the two numbers and return the sum as a linked list, "
        "also in reverse order."),
    constraints="- The number of nodes is in `[1, 100]`.\n- `0 <= Node.val <= 9`",
    time="O(max(n, m))", space="O(max(n, m))",
    fn="addTwoNumbers", params=[("l1", "ListNode"), ("l2", "ListNode")],
    **{"return": "ListNode"},
    ref="""
class Solution:
    def addTwoNumbers(self, l1, l2):
        dummy = ListNode(0)
        cur = dummy
        carry = 0
        while l1 or l2 or carry:
            s = carry
            if l1: s += l1.val; l1 = l1.next
            if l2: s += l2.val; l2 = l2.next
            carry, d = divmod(s, 10)
            cur.next = ListNode(d)
            cur = cur.next
        return dummy.next
""",
    examples=[
        {"input": {"l1": [2, 4, 3], "l2": [5, 6, 4]}, "note": "342+465=807 ⇒ [7,0,8]."},
        {"input": {"l1": [9, 9], "l2": [1]}, "note": "99+1=100 ⇒ [0,0,1]."},
        {"input": {"l1": [0], "l2": [0]}, "note": "⇒ [0]."},
    ],
    gen=lambda rng: [_addtwo_case(rng) for _ in range(24)],
)


def _addtwo_case(rng):
    def num(rng):
        n = rng.randint(1, 5)
        d = [rng.randint(0, 9) for _ in range(n - 1)] + [rng.randint(0, 9)]
        return d
    return {"l1": num(rng), "l2": num(rng)}


_p(
    title="Remove Nth Node From End of List",
    difficulty="MEDIUM",
    tags=["linked-list", "two-pointers"],
    description=(
        "Given the `head` of a linked list, remove the n-th node from the end of "
        "the list and return its head."),
    constraints="- The number of nodes is `sz`, with `1 <= n <= sz <= 30`.",
    time="O(n)", space="O(1)",
    fn="removeNthFromEnd", params=[("head", "ListNode"), ("n", "int")],
    **{"return": "ListNode"},
    ref="""
class Solution:
    def removeNthFromEnd(self, head, n):
        dummy = ListNode(0, head)
        fast = slow = dummy
        for _ in range(n):
            fast = fast.next
        while fast.next:
            fast = fast.next
            slow = slow.next
        slow.next = slow.next.next
        return dummy.next
""",
    examples=[
        {"input": {"head": [1, 2, 3, 4, 5], "n": 2}, "note": "Bỏ 4 ⇒ [1,2,3,5]."},
        {"input": {"head": [1], "n": 1}, "note": "⇒ []."},
        {"input": {"head": [1, 2], "n": 1}, "note": "⇒ [1]."},
    ],
    gen=lambda rng: [_remove_nth_case(rng) for _ in range(24)],
)


def _remove_nth_case(rng):
    sz = rng.randint(1, 12)
    head = ints(rng, sz, 0, 20)
    return {"head": head, "n": rng.randint(1, sz)}


_p(
    title="Kth Largest Element in an Array",
    difficulty="MEDIUM",
    tags=["array", "divide-and-conquer", "sorting", "heap", "quickselect"],
    description=(
        "Given an integer array `nums` and an integer `k`, return the k-th largest "
        "element in the array. Note that it is the k-th largest element in sorted "
        "order, not the k-th distinct element."),
    constraints="- `1 <= k <= nums.length <= 10^5`\n- `-10^4 <= nums[i] <= 10^4`",
    time="O(n)", space="O(1)",
    fn="findKthLargest", params=[("nums", "int[]"), ("k", "int")], **{"return": "int"},
    ref="""
class Solution:
    def findKthLargest(self, nums, k):
        import heapq
        return heapq.nlargest(k, nums)[-1]
""",
    examples=[
        {"input": {"nums": [3, 2, 1, 5, 6, 4], "k": 2}, "note": "Lớn thứ 2 ⇒ 5."},
        {"input": {"nums": [3, 2, 3, 1, 2, 4, 5, 5, 6], "k": 4}, "note": "⇒ 4."},
        {"input": {"nums": [1], "k": 1}, "note": "⇒ 1."},
    ],
    gen=lambda rng: [_kth_largest_case(rng) for _ in range(24)],
)


def _kth_largest_case(rng):
    n = rng.randint(1, 14)
    nums = ints(rng, n, -20, 20)
    return {"nums": nums, "k": rng.randint(1, n)}
