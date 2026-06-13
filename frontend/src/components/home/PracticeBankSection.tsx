import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { ArrowRight, BarChart3, Filter, Flame, Terminal, Trophy } from 'lucide-react';
import { useAuth } from '@/auth/useAuth';

// CTA target for the coding-practice bank: guests register first, signed-in
// users jump straight into the problem list. Admins keep the same console
// mapping as the other landing CTAs would, but here practising is the point.
function usePracticeBankHref(): string {
  const { status } = useAuth();
  return status === 'authenticated' ? '/problems' : '/register';
}

const PERKS = [
  {
    Icon: Terminal,
    title: 'Chấm tự động đa ngôn ngữ',
    body: 'Viết bằng Python, Java, C++ hay JavaScript — code chạy trong sandbox và được chấm trên toàn bộ test case.',
  },
  {
    Icon: Filter,
    title: 'Lọc theo độ khó',
    body: 'Easy, Medium, Hard — chọn đề đúng trình độ và tăng dần thử thách theo lộ trình của bạn.',
  },
  {
    Icon: Trophy,
    title: 'Bảng xếp hạng cộng đồng',
    body: 'Điểm theo độ khó, xếp hạng tuần / tháng và so kè cùng những người luyện tập khác.',
  },
  {
    Icon: Flame,
    title: 'Theo dõi tiến độ & streak',
    body: 'Lịch sử nộp bài, chuỗi ngày luyện liên tục và thống kê theo ngôn ngữ, chủ đề.',
  },
];

export default function PracticeBankSection() {
  const href = usePracticeBankHref();
  return (
    <section
      id="practice-bank"
      className="scroll-mt-16 py-20 px-6 md:px-12 bg-surface-container-low"
    >
      <div className="max-w-7xl mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          {/* Left: pitch + CTA */}
          <div className="lg:col-span-5">
            <span className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-secondary mb-3">
              <BarChart3 className="w-3.5 h-3.5" />
              Luyện thuật toán
            </span>
            <h2 className="text-3xl font-bold text-on-surface mb-4">
              Rèn phản xạ giải thuật mỗi ngày
            </h2>
            <p className="text-on-surface-variant text-lg leading-relaxed mb-8">
              Ngân hàng bài tập kiểu LeetCode với hàng trăm đề đã được kiểm thử. Tự luyện theo
              nhịp của bạn, nộp bài chấm ngay và theo dõi sự tiến bộ qua từng ngày.
            </p>
            <Link
              to={href}
              className="inline-flex items-center gap-2 bg-on-surface text-surface-container-lowest px-8 py-3 rounded-full font-bold hover:scale-105 transition-transform"
            >
              Bắt đầu luyện đề
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>

          {/* Right: perk grid */}
          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-5">
            {PERKS.map(({ Icon, title, body }) => (
              <motion.div
                key={title}
                whileHover={{ y: -5 }}
                className="bg-surface-container-lowest border border-outline-variant p-6 rounded-2xl transition-all group"
              >
                <div className="w-11 h-11 rounded-xl bg-secondary-fixed flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                  <Icon className="w-5 h-5 text-on-secondary-fixed" />
                </div>
                <h3 className="font-bold text-on-surface mb-1.5">{title}</h3>
                <p className="text-on-surface-variant text-sm leading-relaxed">{body}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
