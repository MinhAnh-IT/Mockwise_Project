import { motion } from 'motion/react';
import { Quote } from 'lucide-react';
import { FEATURED_TESTIMONIAL } from '@/data/testimonials';

export default function TestimonialSection() {
  const { quote, author, role, avatar } = FEATURED_TESTIMONIAL;

  return (
    <section
      id="testimonial"
      className="scroll-mt-16 py-24 px-6 bg-surface-container-lowest flex justify-center overflow-hidden relative"
    >
      <div className="absolute top-10 left-10 opacity-5">
        <Quote className="w-40 h-40" />
      </div>
      <div className="max-w-4xl text-center relative z-10">
        <div className="flex justify-center mb-8">
          <Quote className="w-12 h-12 text-secondary fill-secondary/20" />
        </div>
        <motion.blockquote
          initial={{ opacity: 0, scale: 0.95 }}
          whileInView={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8 }}
          className="text-2xl md:text-3xl font-bold text-on-surface italic mb-10 leading-snug"
        >
          {quote}
        </motion.blockquote>
        <div className="inline-flex items-center gap-4 p-2 bg-surface-container rounded-full pr-6">
          <div className="w-12 h-12 rounded-full overflow-hidden shadow-md">
            <img
              src={avatar}
              alt={author}
              className="w-full h-full object-cover"
              referrerPolicy="no-referrer"
            />
          </div>
          <div className="text-left">
            <p className="font-bold text-on-surface leading-tight">{author}</p>
            <p className="text-xs text-on-surface-variant font-medium">{role}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
