import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { BrainCircuit, CheckCircle2, Code2, Layout } from 'lucide-react';
import { useStartHref } from '@/lib/cta';

export default function FeaturesSection() {
  const startHref = useStartHref();
  return (
    <section id="features" className="scroll-mt-16 py-20 px-6 md:px-12 bg-surface">
      <div className="max-w-7xl mx-auto">
        <div className="mb-12 text-center md:text-left">
          <h2 className="text-3xl font-bold text-on-surface mb-2">Ba Trụ Cột Thành Công</h2>
          <p className="text-on-surface-variant text-lg">
            Các buổi thực hành tùy chỉnh cho mọi giai đoạn của quy trình tuyển dụng.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
          <motion.div
            whileHover={{ y: -5 }}
            className="md:col-span-8 bg-surface-container-lowest border border-outline-variant p-8 rounded-2xl flex flex-col justify-between group transition-all"
          >
            <div>
              <div className="w-14 h-14 rounded-2xl bg-secondary-fixed flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <BrainCircuit className="w-7 h-7 text-on-secondary-fixed" />
              </div>
              <h3 className="text-2xl font-bold text-on-surface mb-4">Phỏng vấn Hành vi</h3>
              <p className="text-on-surface-variant leading-relaxed max-w-xl">
                Làm chủ phương pháp STAR với phân tích tính cách bằng AI và phản hồi về trí tuệ
                cảm xúc. Thực hành xử lý các câu hỏi áp lực cao với sự tự tin.
              </p>
            </div>
            <div className="mt-8 flex flex-wrap gap-6">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-secondary" />
                <span className="font-medium text-sm">Phân tích giọng điệu</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-secondary" />
                <span className="font-medium text-sm">Theo dõi ngôn ngữ cơ thể</span>
              </div>
            </div>
          </motion.div>

          <motion.div
            whileHover={{ scale: 1.02 }}
            className="md:col-span-4 bg-secondary-container p-8 rounded-2xl flex flex-col justify-end text-on-secondary-container relative overflow-hidden"
          >
            <div className="absolute top-0 right-0 p-8 opacity-10">
              <Layout className="w-32 h-32" />
            </div>
            <div className="relative z-10">
              <h3 className="text-2xl font-bold mb-3">Kỹ năng Chuyên môn</h3>
              <p className="text-white/80 text-sm mb-6">
                Đi sâu vào lĩnh vực cụ thể của bạn, từ Thiết kế hệ thống đến Quản lý sản phẩm và
                hơn thế nữa.
              </p>
              <Link
                to={startHref}
                className="block text-center w-full py-3 bg-white/20 backdrop-blur-md rounded-xl font-bold border border-white/30 hover:bg-white/30 transition-all text-xs tracking-widest uppercase"
              >
                Khám phá lĩnh vực
              </Link>
            </div>
          </motion.div>

          <motion.div
            whileHover={{ y: -5 }}
            className="md:col-span-4 bg-surface-container-highest p-8 rounded-2xl flex flex-col justify-between border border-outline-variant group transition-all"
          >
            <div className="w-12 h-12 rounded-xl bg-surface-container-lowest flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform shadow-sm border border-outline-variant/30">
              <Code2 className="w-6 h-6 text-secondary" />
            </div>
            <div>
              <h3 className="text-xl font-bold text-on-surface mb-2">Phỏng vấn Lập trình</h3>
              <p className="text-on-surface-variant text-sm leading-relaxed">
                Trình biên tập cộng tác thời gian thực với phản hồi về độ phức tạp thuật toán và
                gợi ý phương pháp thay thế.
              </p>
            </div>
          </motion.div>

          <motion.div className="md:col-span-8 bg-surface-container-low border-2 border-dashed border-outline-variant p-8 rounded-2xl flex flex-col md:flex-row items-center justify-between gap-8">
            <div className="flex flex-col gap-1 items-center md:items-start">
              <span className="text-5xl font-black text-secondary">98%</span>
              <span className="text-[10px] font-bold text-on-surface-variant tracking-[0.2em] uppercase">
                Tỷ lệ học viên thành công
              </span>
            </div>
            <div className="h-10 w-px bg-outline-variant hidden md:block" />
            <div className="text-center md:text-left flex-1 max-w-sm">
              <p className="text-lg font-bold text-on-surface mb-1">Sẵn sàng nhận lời mời làm việc?</p>
              <p className="text-sm text-on-surface-variant">
                Tham gia cùng hơn 50.000 ứng viên ngay hôm nay.
              </p>
            </div>
            <Link
              to={startHref}
              className="bg-on-surface text-surface-container-lowest px-8 py-3 rounded-full font-bold hover:scale-105 transition-transform flex items-center gap-2"
            >
              Bắt đầu ngay
            </Link>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
