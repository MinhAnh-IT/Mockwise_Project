/**
 * Shared building blocks for the admin question-bank screens. Kept in one
 * file (the codebase favours larger cohesive modules over many tiny ones).
 */
import {
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type ButtonHTMLAttributes,
  type SelectHTMLAttributes,
  type InputHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  Layers,
  LayoutDashboard,
  Library,
  Loader2,
  LogOut,
  Menu,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useAuth } from '@/auth/useAuth';
import Avatar from '@/components/ui/Avatar';
import Logo from '@/components/ui/Logo';
import {
  DIFFICULTY_LABEL,
  STATUS_LABEL,
  type Difficulty,
  type QuestionStatus,
} from '@/types/questionBank';

// ── Page shell ─────────────────────────────────────────────────────────────

type Crumb = { label: string; to?: string };

type AdminNavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Match the path exactly (used for the dashboard root). */
  end?: boolean;
};

const ADMIN_NAV: AdminNavItem[] = [
  { to: '/admin', label: 'Tổng quan', icon: LayoutDashboard, end: true },
  { to: '/admin/questions', label: 'Ngân hàng câu hỏi', icon: Library },
  { to: '/admin/blueprints', label: 'Blueprint phỏng vấn', icon: ClipboardList },
  { to: '/admin/interviews', label: 'Buổi phỏng vấn', icon: ClipboardCheck },
  { to: '/admin/profiles', label: 'Người dùng', icon: Users },
  { to: '/admin/catalog', label: 'Vị trí & Cấp độ', icon: Layers },
  { to: '/admin/monitoring', label: 'Giám sát hệ thống', icon: Activity },
];

function AdminSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { profile, signOut } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = async () => {
    onNavigate?.();
    await signOut();
    navigate('/', { replace: true });
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 shrink-0 items-center gap-2 border-b border-outline-variant px-5">
        <Logo className="h-9 w-auto" />
        <span className="rounded-md bg-secondary/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-secondary">
          Admin
        </span>
      </div>

      <nav className="flex-1 space-y-1 overflow-y-auto p-3">
        {ADMIN_NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${
                isActive
                  ? 'bg-secondary/10 text-secondary'
                  : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
              }`
            }
          >
            <Icon className="h-[18px] w-[18px] shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="shrink-0 border-t border-outline-variant p-3">
        <div className="mb-1 flex items-center gap-3 px-2 py-2">
          <Avatar
            src={profile?.avatarUrl}
            fullName={profile?.fullName ?? 'Admin'}
            size="sm"
          />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-on-surface">
              {profile?.fullName ?? 'Quản trị viên'}
            </p>
            <p className="text-xs text-on-surface-variant">Quản trị viên</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleSignOut}
          className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
        >
          <LogOut className="h-[18px] w-[18px]" />
          Đăng xuất
        </button>
      </div>
    </div>
  );
}

/**
 * Sidebar console shell for every /admin page. Deliberately does NOT render
 * the public `<Header/>`/`<Footer/>` — admins live in their own surface,
 * separate from the user-facing app (practice/history/payment).
 */
export function AdminShell({
  title,
  subtitle,
  breadcrumb,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  breadcrumb?: Crumb[];
  actions?: ReactNode;
  children: ReactNode;
}) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-surface">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 border-r border-outline-variant bg-surface-container-lowest lg:block">
        <AdminSidebar />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="absolute left-0 top-0 h-full w-64 bg-surface-container-lowest shadow-xl">
            <AdminSidebar onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-outline-variant bg-surface-container-lowest/85 px-4 py-3 backdrop-blur md:px-8">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-on-surface-variant hover:bg-surface-container-low lg:hidden"
            aria-label="Mở menu"
          >
            <Menu className="h-5 w-5" />
          </button>
          <div className="min-w-0 flex-1">
            {breadcrumb && breadcrumb.length > 0 && (
              <nav className="mb-0.5 flex flex-wrap items-center gap-1 text-xs text-on-surface-variant">
                {breadcrumb.map((c, i) => (
                  <span
                    key={`${c.label}-${i}`}
                    className="flex items-center gap-1"
                  >
                    {i > 0 && <span className="opacity-50">/</span>}
                    {c.to ? (
                      <Link to={c.to} className="hover:text-secondary">
                        {c.label}
                      </Link>
                    ) : (
                      <span>{c.label}</span>
                    )}
                  </span>
                ))}
              </nav>
            )}
            <h1 className="truncate text-lg font-bold text-on-surface md:text-xl">
              {title}
            </h1>
            {subtitle && (
              <p className="truncate text-xs text-on-surface-variant md:text-sm">
                {subtitle}
              </p>
            )}
          </div>
          {actions && (
            <div className="flex shrink-0 items-center gap-2">{actions}</div>
          )}
        </header>

        <main className="flex-1 px-4 py-6 md:px-8 md:py-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  );
}

// ── Pills ──────────────────────────────────────────────────────────────────

export function Pill({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'emerald' | 'amber' | 'red' | 'secondary';
}) {
  const tones: Record<string, string> = {
    neutral: 'bg-surface-container text-on-surface-variant',
    emerald: 'bg-emerald-100 text-emerald-700',
    amber: 'bg-amber-100 text-amber-700',
    red: 'bg-red-100 text-red-700',
    secondary: 'bg-secondary/10 text-secondary',
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

export function StatusPill({ status }: { status: QuestionStatus }) {
  const tone =
    status === 'ACTIVE' ? 'emerald' : status === 'INACTIVE' ? 'amber' : 'neutral';
  return <Pill tone={tone}>{STATUS_LABEL[status]}</Pill>;
}

export function DifficultyPill({ difficulty }: { difficulty: Difficulty }) {
  const tone =
    difficulty === 'EASY'
      ? 'emerald'
      : difficulty === 'MEDIUM'
        ? 'amber'
        : 'red';
  return <Pill tone={tone}>{DIFFICULTY_LABEL[difficulty]}</Pill>;
}

// ── Form primitives ────────────────────────────────────────────────────────

const CONTROL =
  'w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface outline-none transition focus:border-secondary focus:ring-2 focus:ring-secondary/30 disabled:opacity-60';

export function Field({
  label,
  required,
  hint,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-semibold text-on-surface">
        {label}
        {required && <span className="ml-0.5 text-red-600">*</span>}
      </label>
      {children}
      {hint && !error && (
        <p className="mt-1 text-xs text-on-surface-variant">{hint}</p>
      )}
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${CONTROL} ${props.className ?? ''}`} />;
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={`${CONTROL} resize-y font-normal ${props.className ?? ''}`}
    />
  );
}

export function CodeArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      spellCheck={false}
      {...props}
      className={`${CONTROL} resize-y whitespace-pre font-mono text-xs leading-relaxed ${props.className ?? ''}`}
    />
  );
}

export function Select({
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props} className={`${CONTROL} ${props.className ?? ''}`}>
      {children}
    </select>
  );
}

/** `<option>`-friendly select for a string enum + label map. */
export function EnumSelect<T extends string>({
  value,
  onChange,
  options,
  labels,
  placeholder,
  disabled,
}: {
  value: T | '' | undefined;
  onChange: (v: T | '') => void;
  options: T[];
  labels: Record<T, string>;
  placeholder?: string;
  disabled?: boolean;
}) {
  return (
    <Select
      value={value ?? ''}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as T | '')}
    >
      <option value="">{placeholder ?? 'Tất cả'}</option>
      {options.map((o) => (
        <option key={o} value={o}>
          {labels[o]}
        </option>
      ))}
    </Select>
  );
}

// ── Tag input ──────────────────────────────────────────────────────────────

export function TagInput({
  value,
  onChange,
  placeholder = 'Nhập rồi Enter…',
}: {
  value: string[];
  onChange: (next: string[]) => void;
  placeholder?: string;
}) {
  const [draft, setDraft] = useState('');

  const commit = (raw: string) => {
    const parts = raw
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (!parts.length) return;
    const merged = Array.from(new Set([...value, ...parts]));
    onChange(merged);
    setDraft('');
  };

  return (
    <div className="flex flex-wrap items-center gap-1.5 rounded-xl border border-outline-variant bg-surface-container-lowest px-2 py-1.5 focus-within:border-secondary focus-within:ring-2 focus-within:ring-secondary/30">
      {value.map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center gap-1 rounded-md bg-surface-container px-2 py-0.5 text-xs text-on-surface"
        >
          {tag}
          <button
            type="button"
            onClick={() => onChange(value.filter((t) => t !== tag))}
            className="text-on-surface-variant hover:text-red-600"
            aria-label={`Xoá ${tag}`}
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            commit(draft);
          } else if (e.key === 'Backspace' && !draft && value.length) {
            onChange(value.slice(0, -1));
          }
        }}
        onBlur={() => draft && commit(draft)}
        placeholder={value.length ? '' : placeholder}
        className="min-w-[8rem] flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
      />
    </div>
  );
}

// ── Multi-select chips (e.g. target roles) ─────────────────────────────────

export function ChipMultiSelect<T extends string>({
  value,
  onChange,
  options,
  labels,
}: {
  value: T[];
  onChange: (next: T[]) => void;
  options: T[];
  labels: Record<T, string>;
}) {
  const toggle = (o: T) =>
    onChange(value.includes(o) ? value.filter((v) => v !== o) : [...value, o]);
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <button
            key={o}
            type="button"
            onClick={() => toggle(o)}
            className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
              on
                ? 'border-secondary bg-secondary text-on-secondary'
                : 'border-outline-variant bg-surface-container-lowest text-on-surface-variant hover:border-secondary'
            }`}
          >
            {labels[o]}
          </button>
        );
      })}
    </div>
  );
}

// ── Buttons ────────────────────────────────────────────────────────────────

export function Button({
  variant = 'primary',
  loading,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost' | 'danger' | 'outline';
  loading?: boolean;
  children: ReactNode;
}) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition disabled:opacity-60';
  const variants: Record<string, string> = {
    primary: 'bg-secondary text-on-secondary hover:opacity-90',
    outline:
      'border border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low',
    ghost: 'text-on-surface-variant hover:bg-surface-container-low',
    danger: 'bg-red-600 text-white hover:bg-red-700',
  };
  return (
    <button
      {...props}
      type={props.type ?? 'button'}
      disabled={props.disabled || loading}
      className={`${base} ${variants[variant]} ${props.className ?? ''}`}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />}
      {children}
    </button>
  );
}

// ── Confirm dialog ─────────────────────────────────────────────────────────

export function ConfirmDialog({
  open,
  title,
  message,
  confirmText = 'Xác nhận',
  danger,
  loading,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmText?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      onClick={onCancel}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-bold text-on-surface">{title}</h3>
        <div className="mt-2 text-sm text-on-surface-variant">{message}</div>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={loading}>
            Huỷ
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
          >
            {confirmText}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Toast (single, transient) ──────────────────────────────────────────────

export type ToastState = { kind: 'success' | 'error'; text: string } | null;

export function Toast({
  toast,
  onClose,
}: {
  toast: ToastState;
  onClose: () => void;
}) {
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (!toast) return;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(onClose, 4000);
    return () => window.clearTimeout(timer.current);
  }, [toast, onClose]);

  if (!toast) return null;
  const ok = toast.kind === 'success';
  return (
    <div className="fixed right-6 top-20 z-[70] flex max-w-sm items-start gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 shadow-lg">
      {ok ? (
        <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" />
      ) : (
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
      )}
      <p className="text-sm text-on-surface">{toast.text}</p>
      <button
        type="button"
        onClick={onClose}
        className="text-on-surface-variant hover:text-on-surface"
        aria-label="Đóng"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

// ── Misc states ────────────────────────────────────────────────────────────

export function Spinner({ label = 'Đang tải…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-on-surface-variant">
      <Loader2 className="h-5 w-5 animate-spin" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <AlertCircle className="h-8 w-8 text-red-600" />
      <p className="text-sm text-on-surface-variant">{message}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-outline-variant py-16 text-center text-sm text-on-surface-variant">
      {message}
    </div>
  );
}
