import type { LucideIcon } from 'lucide-react';
import { BrainCircuit, Briefcase, Code2 } from 'lucide-react';

export type PracticeType = 'behavioral' | 'core' | 'coding';

export type PracticeOption = {
  id: PracticeType;
  title: string;
  shortDescription: string;
  Icon: LucideIcon;
  /** Tailwind classes for the icon tile background. */
  accentClassName: string;
  /**
   * Pre-session readiness details. Length and question count are decided by
   * the backend (mocked here until the interview-orchestrator service exists),
   * so the user just sees them on the readiness screen — they don't tune
   * anything.
   */
  readiness: {
    estimatedMinutes: string;
    questionCount: string;
    longDescription: string;
    checklist: string[];
  };
};

export const PRACTICE_OPTIONS: PracticeOption[] = [
  {
    id: 'behavioral',
    title: 'Phỏng vấn Hành vi',
    shortDescription: 'Câu hỏi tình huống — luyện cấu trúc trả lời theo phương pháp STAR.',
    Icon: BrainCircuit,
    accentClassName: 'bg-secondary-fixed text-on-secondary-fixed',
    readiness: {
      estimatedMinutes: '20–25 phút',
      questionCount: '5–7 câu',
      longDescription:
        'AI sẽ đặt các câu hỏi tình huống dựa trên kinh nghiệm trong hồ sơ của bạn và đánh giá phản hồi theo các tiêu chí: rõ ràng, cảm xúc, thuyết phục.',
      checklist: [
        'Tìm chỗ yên tĩnh và bật micro',
        'Chuẩn bị 1–2 ví dụ thật từ kinh nghiệm bản thân',
        'Sẵn sàng trả lời theo cấu trúc STAR',
      ],
    },
  },
  {
    id: 'core',
    title: 'Kỹ năng Chuyên môn',
    shortDescription: 'Câu hỏi lý thuyết & tình huống đặc thù cho lĩnh vực bạn ứng tuyển.',
    Icon: Briefcase,
    accentClassName: 'bg-secondary/10 text-secondary',
    readiness: {
      estimatedMinutes: '25–35 phút',
      questionCount: '6–8 câu',
      longDescription:
        'Hệ thống chọn câu hỏi theo lĩnh vực và cấp độ trong hồ sơ. Có thể có câu follow-up để kiểm tra độ sâu hiểu biết.',
      checklist: [
        'Tìm chỗ yên tĩnh và bật micro',
        'Sẵn sàng giải thích bằng lời, không chỉ trả lời ngắn',
        'Có giấy nháp nếu bạn muốn vẽ sơ đồ',
      ],
    },
  },
  {
    id: 'coding',
    title: 'Phỏng vấn Lập trình',
    shortDescription: 'Bài lập trình LeetCode-style, chấm tự động qua sandbox.',
    Icon: Code2,
    accentClassName: 'bg-emerald-100 text-emerald-700',
    readiness: {
      estimatedMinutes: '45–60 phút',
      questionCount: '1–2 bài',
      longDescription:
        'Bạn viết code trong trình biên tập tích hợp, nộp để chạy test case, và nhận phản hồi về độ phức tạp cùng hướng tối ưu.',
      checklist: [
        'Đảm bảo bàn phím & màn hình đủ thoải mái',
        'Chọn ngôn ngữ bạn quen nhất khi vào phiên',
        'Sẵn sàng giải thích cách tiếp cận trước khi viết code',
      ],
    },
  },
];

export function findPracticeOption(id: string): PracticeOption | undefined {
  return PRACTICE_OPTIONS.find((option) => option.id === id);
}
