import { motion } from 'motion/react';

export default function CtaSection() {
  return (
    <section className="py-24 px-6 md:px-12">
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        whileInView={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        className="max-w-7xl mx-auto bg-slate-950 rounded-[3rem] p-12 md:p-24 text-center relative overflow-hidden"
      >
        <div className="absolute inset-0 bg-grid-pattern opacity-10 pointer-events-none" />
        <div className="absolute -top-24 -left-24 w-64 h-64 bg-secondary/20 blur-[100px] rounded-full" />
        <div className="absolute -bottom-24 -right-24 w-64 h-64 bg-secondary/10 blur-[100px] rounded-full" />

        <div className="relative z-10">
          <h2 className="text-4xl md:text-5xl font-extrabold text-white mb-8 tracking-tight">
            Ngừng suy đoán. Bắt đầu làm chủ.
          </h2>
          <p className="text-slate-400 text-lg mb-12 max-w-xl mx-auto leading-relaxed">
            Xây dựng phản xạ cần thiết cho những vị trí kỹ thuật cạnh tranh nhất thế giới.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <button
              type="button"
              className="bg-white text-slate-950 px-10 py-4 rounded-2xl font-bold hover:bg-slate-100 transition-colors shadow-xl"
            >
              Tạo tài khoản của bạn
            </button>
            <button
              type="button"
              className="bg-white/5 backdrop-blur-md border border-white/10 text-white px-10 py-4 rounded-2xl font-bold hover:bg-white/10 transition-colors"
            >
              Bảng giá
            </button>
          </div>
        </div>
      </motion.div>
    </section>
  );
}
