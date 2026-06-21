import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  Crown,
  Flame,
  Loader2,
  Medal,
  Target,
  TrendingUp,
  Trophy,
  Users,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getCommunity, getLeaderboard } from '@/api/practice';
import Avatar from '@/components/ui/Avatar';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type {
  CommunityResponse,
  LeaderboardEntry,
  LeaderboardResponse,
  LeaderboardWindow,
} from '@/types/practice';

const WINDOWS: { value: LeaderboardWindow; label: string }[] = [
  { value: 'ALL', label: 'Mọi lúc' },
  { value: 'MONTH', label: 'Tháng này' },
  { value: 'WEEK', label: 'Tuần này' },
];

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
};

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'text-emerald-600',
  MEDIUM: 'text-amber-600',
  HARD: 'text-rose-600',
};

function displayName(e: { fullName: string | null; userId: string }): string {
  if (e.fullName && e.fullName.trim()) return e.fullName.trim();
  return `Người dùng ${e.userId.slice(0, 4)}`;
}

/** Same-origin avatar path — the shared <Avatar> fetches it with auth and falls back to initials. */
const avatarSrc = (userId: string) => `/api/v1/storage/avatars/${userId}`;

export default function StatsPage() {
  const [windowSel, setWindowSel] = useState<LeaderboardWindow>('ALL');
  const [board, setBoard] = useState<LeaderboardResponse | null>(null);
  const [community, setCommunity] = useState<CommunityResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Community is window-independent — fetch once.
  useEffect(() => {
    let cancelled = false;
    getCommunity()
      .then((c) => !cancelled && setCommunity(c))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getLeaderboard(windowSel, 50)
      .then((b) => !cancelled && setBoard(b))
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof ApiError ? err.message : 'Không tải được bảng xếp hạng.');
        setBoard(null);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [windowSel]);

  const podium = board?.entries.slice(0, 3) ?? [];
  const rest = board?.entries.slice(3) ?? [];

  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 sm:px-6 pb-24 pt-28">
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-amber-100 text-amber-600">
              <Trophy className="h-6 w-6" />
            </span>
            <div>
              <h1 className="text-2xl font-bold text-on-surface">Bảng xếp hạng</h1>
              <p className="text-sm text-on-surface-variant">
                Điểm tính theo độ khó · Easy 1 · Medium 3 · Hard 5.
              </p>
            </div>
          </div>
          <div className="flex gap-2 sm:ml-auto">
            <Link
              to="/problems/submissions"
              className="flex-1 rounded-xl border border-outline-variant px-3.5 py-2 text-center text-sm font-semibold text-on-surface-variant transition-colors hover:text-on-surface sm:flex-none"
            >
              Tiến độ
            </Link>
            <Link
              to="/problems"
              className="flex-1 rounded-xl border border-outline-variant px-3.5 py-2 text-center text-sm font-semibold text-on-surface-variant transition-colors hover:text-on-surface sm:flex-none"
            >
              Danh sách đề
            </Link>
          </div>
        </div>

        {/* Window tabs */}
        <div className="mb-5 inline-flex rounded-xl border border-outline-variant p-0.5">
          {WINDOWS.map((w) => (
            <button
              key={w.value}
              type="button"
              onClick={() => setWindowSel(w.value)}
              className={`rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors ${
                windowSel === w.value
                  ? 'bg-on-surface text-surface'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              {w.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="flex items-center justify-center gap-2 py-24 text-sm text-on-surface-variant">
            <Loader2 className="h-4 w-4 animate-spin" /> Đang tải…
          </div>
        ) : error ? (
          <div className="py-24 text-center text-sm text-rose-600">{error}</div>
        ) : (
          <div className="space-y-6">
            {board?.me && (
              <MyStanding me={board.me} total={board.totalParticipants} />
            )}

            {podium.length > 0 ? (
              <>
                <Podium entries={podium} />

                {rest.length > 0 && (
                  <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-sm">
                    <div className="grid grid-cols-[3rem_1fr_auto_4rem_4rem] items-center gap-3 border-b border-outline-variant/60 bg-surface-container-low/50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                      <span className="text-center">Hạng</span>
                      <span>Người dùng</span>
                      <span className="hidden text-center sm:block">E / M / H</span>
                      <span className="text-right">Bài</span>
                      <span className="text-right">Điểm</span>
                    </div>
                    <ul>
                      {rest.map((e) => (
                        <LeaderRow key={e.userId} entry={e} highlight={e.rank === board?.me?.rank} />
                      ))}
                    </ul>
                  </div>
                )}
              </>
            ) : (
              <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest py-16 text-center text-sm text-on-surface-variant shadow-sm">
                Chưa có ai trên bảng xếp hạng cho mốc thời gian này.
              </div>
            )}

            {/* Community */}
            {community && (
              <div className="grid gap-6 md:grid-cols-2">
                <CommunityCard
                  title="Thịnh hành tuần này"
                  icon={<TrendingUp className="h-4 w-4" />}
                  empty="Chưa có hoạt động trong tuần."
                  items={community.trending.map((t) => ({
                    problemId: t.problemId,
                    title: t.title ?? t.problemId,
                    difficulty: t.difficulty,
                    people: t.participants,
                  }))}
                />
                <CommunityCard
                  title="Khó nhằn nhất"
                  icon={<Flame className="h-4 w-4" />}
                  empty="Chưa đủ dữ liệu."
                  items={community.hardest.map((h) => ({
                    problemId: h.problemId,
                    title: h.title ?? h.problemId,
                    difficulty: h.difficulty,
                    progress: { rate: h.solveRate, attempters: h.attempters },
                  }))}
                />
              </div>
            )}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}

// ── My standing ────────────────────────────────────────────────────────────

function MyStanding({
  me,
  total,
}: {
  me: NonNullable<LeaderboardResponse['me']>;
  total: number;
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-8 gap-y-3 rounded-2xl border border-secondary/30 bg-secondary/5 px-6 py-4">
      <div className="flex items-center gap-2">
        <Target className="h-5 w-5 text-secondary" />
        <span className="text-sm font-semibold text-on-surface">Vị trí của bạn</span>
      </div>
      <Stat label="Hạng" value={`#${me.rank}`} sub={`/ ${total}`} />
      <Stat label="Top" value={`${me.topPercent}%`} />
      <Stat label="Điểm" value={String(me.score)} />
      <Stat label="Đã giải" value={String(me.solved)} />
    </div>
  );
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div>
      <p className="text-[11px] font-medium uppercase tracking-wide text-on-surface-variant">{label}</p>
      <p className="text-lg font-bold tabular-nums text-on-surface">
        {value}
        {sub && <span className="ml-1 text-xs font-normal text-on-surface-variant">{sub}</span>}
      </p>
    </div>
  );
}

// ── Podium (top 3) ─────────────────────────────────────────────────────────

const PODIUM_STYLE = [
  { ring: 'ring-amber-400', badge: 'bg-amber-400 text-amber-950', icon: <Crown className="h-4 w-4" />, order: 'order-2 sm:-mt-4' },
  { ring: 'ring-slate-300', badge: 'bg-slate-300 text-slate-800', icon: <Medal className="h-4 w-4" />, order: 'order-1' },
  { ring: 'ring-orange-300', badge: 'bg-orange-300 text-orange-950', icon: <Medal className="h-4 w-4" />, order: 'order-3' },
];

function Podium({ entries }: { entries: LeaderboardEntry[] }) {
  return (
    <div className="flex items-end justify-center gap-2 sm:gap-6">
      {entries.map((e, i) => {
        const st = PODIUM_STYLE[i];
        return (
          <div
            key={e.userId}
            className={`flex w-24 flex-col items-center rounded-2xl border border-outline-variant bg-surface-container-lowest px-2 py-4 shadow-sm sm:w-36 sm:px-3 ${st.order}`}
          >
            <span className={`relative rounded-full ring-2 ${st.ring}`}>
              <Avatar src={avatarSrc(e.userId)} fullName={displayName(e)} size="lg" />
              <span className={`absolute -bottom-1 -right-1 grid h-6 w-6 place-items-center rounded-full text-[11px] font-bold ${st.badge}`}>
                {e.rank}
              </span>
            </span>
            <p className="mt-3 w-full truncate text-center text-sm font-semibold text-on-surface" title={displayName(e)}>
              {displayName(e)}
            </p>
            <p className="mt-0.5 inline-flex items-center gap-1 text-sm font-bold text-secondary">
              <span className="text-on-surface-variant">{st.icon}</span>
              {e.score} đ
            </p>
            <p className="text-xs text-on-surface-variant">{e.solved} bài</p>
          </div>
        );
      })}
    </div>
  );
}

// ── Leaderboard row ────────────────────────────────────────────────────────

function LeaderRow({ entry, highlight }: { entry: LeaderboardEntry; highlight?: boolean }) {
  return (
    <li
      className={`grid grid-cols-[3rem_1fr_auto_4rem_4rem] items-center gap-3 border-b border-outline-variant/40 px-4 py-2.5 last:border-b-0 ${
        highlight ? 'bg-secondary/5' : ''
      }`}
    >
      <span className="text-center text-sm font-semibold tabular-nums text-on-surface-variant">
        {entry.rank}
      </span>
      <div className="flex min-w-0 items-center gap-2.5">
        <Avatar src={avatarSrc(entry.userId)} fullName={displayName(entry)} size="sm" />
        <span className="truncate text-sm font-medium text-on-surface" title={displayName(entry)}>
          {displayName(entry)}
          {highlight && <span className="ml-1 text-xs font-semibold text-secondary">(bạn)</span>}
        </span>
      </div>
      <span className="hidden items-center gap-1.5 text-xs font-semibold tabular-nums sm:flex">
        <span className="text-emerald-600">{entry.easy}</span>
        <span className="text-on-surface-variant">·</span>
        <span className="text-amber-600">{entry.medium}</span>
        <span className="text-on-surface-variant">·</span>
        <span className="text-rose-600">{entry.hard}</span>
      </span>
      <span className="text-right text-sm tabular-nums text-on-surface-variant">{entry.solved}</span>
      <span className="text-right text-sm font-bold tabular-nums text-on-surface">{entry.score}</span>
    </li>
  );
}

// ── Solve-rate progress bar ────────────────────────────────────────────────

/** Mini progress bar for a problem's solve rate; colour shifts red→amber→green.
 *  Attempters are kept on the hover tooltip to stay compact. */
function SolveRateBar({ rate, attempters }: { rate: number; attempters: number }) {
  const pct = Math.round(rate * 100);
  const fill = rate < 0.4 ? 'bg-rose-500' : rate < 0.7 ? 'bg-amber-500' : 'bg-emerald-500';
  const text = rate < 0.4 ? 'text-rose-600' : rate < 0.7 ? 'text-amber-600' : 'text-emerald-600';
  return (
    <span
      className="flex w-28 shrink-0 items-center justify-end gap-2"
      title={`${pct}% giải được · ${attempters} người thử`}
    >
      <span className="h-1.5 w-14 overflow-hidden rounded-full bg-outline-variant/40">
        <span
          className={`block h-full rounded-full ${fill}`}
          style={{ width: `${Math.max(pct, 4)}%` }}
        />
      </span>
      <span className={`w-8 text-right text-xs font-semibold tabular-nums ${text}`}>{pct}%</span>
    </span>
  );
}

// ── Community card ─────────────────────────────────────────────────────────

type CommunityItem = {
  problemId: string;
  title: string;
  difficulty: string | null;
  /** Plain text meta — used when there's neither a progress bar nor a people count. */
  meta?: string;
  /** Participant count, rendered as a person icon + number. */
  people?: number;
  /** Solve-rate progress bar; attempters surfaced on hover. */
  progress?: { rate: number; attempters: number };
};

function CommunityCard({
  title,
  icon,
  items,
  empty,
}: {
  title: string;
  icon: ReactNode;
  items: CommunityItem[];
  empty: string;
}) {
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
      <div className="mb-4 flex items-center gap-2 text-sm font-bold text-on-surface">
        <span className="text-on-surface-variant">{icon}</span>
        {title}
      </div>
      {items.length === 0 ? (
        <p className="py-6 text-center text-sm text-on-surface-variant">{empty}</p>
      ) : (
        <ul className="space-y-1">
          {items.map((it, i) => (
            <li key={it.problemId}>
              <Link
                to={`/problems/${it.problemId}`}
                className="flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-surface-container-low"
              >
                <span className="w-5 text-center text-xs font-semibold tabular-nums text-on-surface-variant">
                  {i + 1}
                </span>
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">
                  {it.title}
                </span>
                {it.difficulty && (
                  <span className={`shrink-0 text-xs font-semibold ${DIFFICULTY_TONE[it.difficulty] ?? 'text-on-surface-variant'}`}>
                    {DIFFICULTY_LABEL[it.difficulty] ?? it.difficulty}
                  </span>
                )}
                {it.progress ? (
                  <SolveRateBar rate={it.progress.rate} attempters={it.progress.attempters} />
                ) : it.people != null ? (
                  <span
                    className="flex shrink-0 items-center gap-1 text-xs font-semibold tabular-nums text-on-surface-variant"
                    title={`${it.people} người làm`}
                  >
                    <Users className="h-3.5 w-3.5" />
                    {it.people}
                  </span>
                ) : (
                  <span className="w-24 shrink-0 text-right text-xs text-on-surface-variant">
                    {it.meta}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
