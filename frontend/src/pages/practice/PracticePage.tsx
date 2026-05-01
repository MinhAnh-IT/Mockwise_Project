import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '@/auth/useAuth';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { PRACTICE_OPTIONS, type PracticeOption } from '@/data/practice';

/**
 * Practice landing page — three cards, the user only picks a type. Length,
 * question count and difficulty are decided by the system on the readiness
 * screen at /practice/:type.
 */
export default function PracticePage() {
  const { profile } = useAuth();
  const greetingName = profile?.fullName.split(' ').pop() ?? null;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-5xl mx-auto">
          <div className="mb-10 text-center md:text-left">
            <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-2">
              {greetingName ? `Chào ${greetingName}, bạn muốn luyện gì?` : 'Bạn muốn luyện gì?'}
            </h1>
            <p className="text-on-surface-variant text-sm">
              Chọn một loại — hệ thống sẽ tự chọn câu hỏi phù hợp với hồ sơ của bạn.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            {PRACTICE_OPTIONS.map((option) => (
              <PracticeCard key={option.id} option={option} />
            ))}
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}

function PracticeCard({ option }: { option: PracticeOption }) {
  return (
    <Link
      to={`/practice/${option.id}`}
      className="group flex flex-col bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 hover:border-secondary/40 hover:shadow-md transition-all"
    >
      <div
        className={`w-12 h-12 rounded-xl flex items-center justify-center mb-5 ${option.accentClassName}`}
      >
        <option.Icon className="w-6 h-6" />
      </div>

      <h2 className="text-lg font-bold text-on-surface mb-2">{option.title}</h2>
      <p className="text-sm text-on-surface-variant leading-relaxed mb-6 flex-1">
        {option.shortDescription}
      </p>

      <div className="flex items-center gap-1.5 text-sm font-semibold text-secondary group-hover:gap-2.5 transition-all">
        Bắt đầu
        <ArrowRight className="w-4 h-4" />
      </div>
    </Link>
  );
}
