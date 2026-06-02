import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { fadeIn, staggerContainer } from '@/lib/animations';
import { useStartHref } from '@/lib/cta';
import { SITE } from '@/data/site';

export default function HeroSection() {
  const startHref = useStartHref();
  return (
    <section className="relative overflow-hidden bg-surface-container-lowest px-6 py-20 md:py-32 flex flex-col items-center text-center">
      <motion.div
        className="max-w-7xl w-full"
        initial="initial"
        animate="animate"
        variants={staggerContainer}
      >
        <motion.span
          variants={fadeIn}
          className="inline-block px-3 py-1 bg-secondary-fixed text-on-secondary-fixed text-xs font-semibold tracking-wider uppercase rounded-full mb-6"
        >
          Chuẩn bị với sức mạnh AI
        </motion.span>
        <motion.h1
          variants={fadeIn}
          className="text-4xl md:text-5xl lg:text-6xl font-extrabold text-on-surface mb-6 max-w-4xl mx-auto leading-tight"
        >
          Làm chủ buổi phỏng vấn tiếp theo với{' '}
          <span className="text-secondary">{SITE.name}</span>
        </motion.h1>
        <motion.p
          variants={fadeIn}
          className="text-lg text-on-surface-variant max-w-2xl mx-auto mb-10"
        >
          Người cố vấn thầm lặng trên trình duyệt của bạn. Thực hành phỏng vấn hành vi, kỹ thuật và
          lập trình với phản hồi thời gian thực được thiết kế cho sự chuyên nghiệp xuất sắc.
        </motion.p>
        <motion.div
          variants={fadeIn}
          className="flex flex-col sm:flex-row gap-4 justify-center mb-16"
        >
          <Link
            to={startHref}
            className="bg-secondary text-on-secondary px-8 py-3 rounded-xl font-semibold hover:opacity-90 transition-all flex items-center justify-center gap-2 shadow-lg shadow-secondary/20"
          >
            Bắt đầu miễn phí
            <ArrowRight className="w-5 h-5" />
          </Link>
          <Link
            to={startHref}
            className="border border-outline-variant text-on-surface px-8 py-3 rounded-xl font-semibold hover:bg-surface-container-low transition-all flex items-center justify-center"
          >
            Xem bản dùng thử
          </Link>
        </motion.div>

        <motion.div variants={fadeIn} className="relative w-full max-w-5xl mx-auto">
          <div className="absolute -inset-x-10 -bottom-10 top-1/3 bg-secondary/15 blur-3xl rounded-full pointer-events-none" />
          <img
            src={SITE.heroMockupImage}
            alt={SITE.heroMockupAlt}
            className="relative w-full h-auto drop-shadow-2xl"
            loading="eager"
            decoding="async"
          />
        </motion.div>
      </motion.div>
    </section>
  );
}
