import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '@/auth/useAuth';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { PRACTICE_OPTIONS, type PracticeOption } from '@/data/practice';

/**
 * Practice landing page — minimal by design. The user only picks a type;
 * everything else (questions, length, difficulty) is decided by the system
 * once they confirm on the readiness screen at /practice/:type.
 */
export default function PracticePage() {
  const { profile } = useAuth();
  const greetingName = profile?.fullName.split(' ').pop() ?? null;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-3xl mx-auto">
          <div className="mb-10">
            <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-2">
              {greetingName ? `Chào ${greetingName}, bạn muốn luyện gì?` : 'Bạn muốn luyện gì?'}
            </h1>
            <p className="text-on-surface-variant text-sm">
              Chọn một loại — hệ thống sẽ tự chọn câu hỏi phù hợp với hồ sơ của bạn.
            </p>
          </div>

          <div className="space-y-3">
            {PRACTICE_OPTIONS.map((option) => (
              <PracticeRow key={option.id} option={option} />
            ))}
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}

function PracticeRow({ option }: { option: PracticeOption }) {
  return (
    <Link
      to={`/practice/${option.id}`}
      className="group flex items-center gap-5 bg-surface-container-lowest border border-outline-variant rounded-2xl p-5 hover:border-secondary/40 hover:shadow-sm transition-all"
    >
      <div
        className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${option.accentClassName}`}
      >
        <option.Icon className="w-6 h-6" />
      </div>

      <div className="flex-1 min-w-0">
        <h2 className="text-base font-bold text-on-surface mb-0.5">{option.title}</h2>
        <p className="text-sm text-on-surface-variant truncate">{option.shortDescription}</p>
      </div>

      <ArrowRight className="w-5 h-5 text-on-surface-variant group-hover:text-secondary group-hover:translate-x-0.5 transition-all flex-shrink-0" />
    </Link>
  );
}
