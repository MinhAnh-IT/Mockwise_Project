import type { CodingProblemView } from '@/types/coding';

/**
 * Demo-only fallback. The workspace uses this when
 * GET /questions/{sqid}/coding returns 404/501 (backend not implemented
 * yet) so the LeetCode UI is fully clickable for review. DELETE this file
 * and the fallback branch in CodingWorkspace once the BE contract ships.
 */
export function buildMockProblem(
  sessionQuestionId: string,
  sequence: number,
): CodingProblemView {
  return {
    sessionQuestionId,
    sequence,
    title: 'Two Sum',
    optimalTimeComplexity: 'O(n)',
    optimalSpaceComplexity: 'O(n)',
    description: [
      'Cho một mảng số nguyên `nums` và một số nguyên `target`, trả về **chỉ số** của hai phần tử sao cho tổng của chúng bằng `target`.',
      '',
      'Giả thiết mỗi đầu vào có **đúng một** lời giải, và không dùng cùng một phần tử hai lần. Bạn có thể trả về kết quả theo bất kỳ thứ tự nào.',
    ].join('\n'),
    constraints: [
      '- 2 ≤ nums.length ≤ 10^4',
      '- -10^9 ≤ nums[i] ≤ 10^9',
      '- -10^9 ≤ target ≤ 10^9',
      '- Luôn tồn tại đúng một lời giải.',
    ].join('\n'),
    functionMeta: {
      fn: 'twoSum',
      params: [
        { name: 'nums', type: 'int[]' },
        { name: 'target', type: 'int' },
      ],
      returnType: 'int[]',
      orderMatters: false,
      inPlace: false,
    },
    starterCode: {
      java: [
        'class Solution {',
        '    public int[] twoSum(int[] nums, int target) {',
        '        // viết code ở đây',
        '    }',
        '}',
      ].join('\n'),
      python: [
        'class Solution:',
        '    def twoSum(self, nums: list[int], target: int) -> list[int]:',
        '        # viết code ở đây',
        '        pass',
      ].join('\n'),
      cpp: [
        '#include <vector>',
        'using namespace std;',
        '',
        'class Solution {',
        'public:',
        '    vector<int> twoSum(vector<int>& nums, int target) {',
        '        // viết code ở đây',
        '    }',
        '};',
      ].join('\n'),
      javascript: [
        '/**',
        ' * @param {number[]} nums',
        ' * @param {number} target',
        ' * @return {number[]}',
        ' */',
        'var twoSum = function(nums, target) {',
        '    // viết code ở đây',
        '};',
      ].join('\n'),
    },
    sampleTestCases: [
      {
        id: 'sample-1',
        inputData: { nums: [2, 7, 11, 15], target: 9 },
        expectedOutput: { result: [0, 1] },
        note: 'Vì nums[0] + nums[1] == 2 + 7 == 9 nên trả về [0, 1].',
      },
      {
        id: 'sample-2',
        inputData: { nums: [3, 2, 4], target: 6 },
        expectedOutput: { result: [1, 2] },
        note: 'nums[1] + nums[2] == 2 + 4 == 6 nên trả về [1, 2].',
      },
    ],
  };
}
