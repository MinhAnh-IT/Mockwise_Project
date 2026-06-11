import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Award, Flame, Loader2, Target, Trophy } from 'lucide-react';
import { ApiError } from '@/api/client';
import { getStats } from '@/api/practice';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type { UserStats } from '@/types/practice';

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Dễ',
  MEDIUM: 'Trung bình',
  HARD: 'Khó',
};

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'text-emerald-600',
  MEDIUM: 'text-amber-600',
  HARD: 'text-rose-600',
};

export default function StatsPage() {
  const [stats, setStats] = useState<UserStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getStats()
      .then((s) => !cancelled && setStats(s))
      .catch((err) =>
        !cancelled && setError(err instanceof ApiError ? err.message : 'Không tải được thống kê.'),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="min-h-screen bg-surface">
      <Header />
      <main className="mx-auto max-w-4xl px-6 pb-24 pt-28">
        <div className="mb-6 flex items-center gap-3">
          <h1 className="text-2xl font-bold text-on-surface">Thống kê luyện đề</h1>
          <Link
            to="/problems"
            className="ml-auto rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant hover:text-on-surface transition-colors"
          >
            Danh sách đề
          </Link>
        </div>

        {loading ? (
          <div className="flex items-center justify-center gap-2 py-20 text-sm text-on-surface-variant">
            <Loader2 className="h-4 w-4 animate-spin" /> Đang tải…
          </div>
        ) : error ? (
          <div className="py-20 text-center text-sm text-rose-600">{error}</div>
        ) : stats ? (
          <div className="space-y-6">
            {/* Headline cards */}
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <StatCard icon={<Trophy className="h-5 w-5" />} label="Đã giải" value={String(stats.solvedTotal)} tone="text-emerald-600" />
              <StatCard icon={<Target className="h-5 w-5" />} label="Đã thử" value={String(stats.attemptedTotal)} tone="text-sky-600" />
              <StatCard
                icon={<Award className="h-5 w-5" />}
                label="Tỉ lệ AC"
                value={stats.acceptanceRate == null ? '—' : `${Math.round(stats.acceptanceRate * 100)}%`}
                tone="text-violet-600"
              />
              <StatCard icon={<Flame className="h-5 w-5" />} label="Chuỗi hiện tại" value={`${stats.currentStreakDays} ngày`} tone="text-amber-600" />
            </div>

            {/* By difficulty */}
            <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5">
              <h2 className="mb-4 text-sm font-semibold text-on-surface">Đã giải theo độ khó</h2>
              <div className="grid grid-cols-3 gap-4">
                {(['EASY', 'MEDIUM', 'HARD'] as const).map((d) => (
                  <div key={d} className="text-center">
                    <p className={`text-3xl font-bold ${DIFFICULTY_TONE[d]}`}>
                      {stats.solvedByDifficulty?.[d] ?? 0}
                    </p>
                    <p className="mt-1 text-xs text-on-surface-variant">{DIFFICULTY_LABEL[d]}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Streak */}
            <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5">
              <h2 className="mb-3 text-sm font-semibold text-on-surface">Chuỗi ngày giải bài</h2>
              <div className="flex gap-8">
                <div>
                  <p className="text-2xl font-bold text-amber-600">{stats.currentStreakDays}</p>
                  <p className="text-xs text-on-surface-variant">Chuỗi hiện tại (ngày)</p>
                </div>
                <div>
                  <p className="text-2xl font-bold text-on-surface">{stats.longestStreakDays}</p>
                  <p className="text-xs text-on-surface-variant">Chuỗi dài nhất (ngày)</p>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </main>
      <Footer />
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  tone: string;
}) {
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <span className={`flex items-center gap-1.5 text-xs font-medium text-on-surface-variant`}>
        <span className={tone}>{icon}</span>
        {label}
      </span>
      <p className={`mt-2 text-2xl font-bold ${tone}`}>{value}</p>
    </div>
  );
}
