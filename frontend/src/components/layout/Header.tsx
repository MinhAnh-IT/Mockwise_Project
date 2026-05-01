import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ChevronDown,
  ClipboardList,
  CreditCard,
  LogOut,
  UserCircle,
} from 'lucide-react';
import { useAuth } from '@/auth/useAuth';
import Logo from '@/components/ui/Logo';
import {
  HEADER_NAV_GUEST,
  HEADER_NAV_USER,
  USER_MENU_ITEMS,
  type NavItem,
} from '@/data/navigation';

const USER_MENU_ICONS: Record<string, ReactNode> = {
  '/profile': <UserCircle className="w-4 h-4" />,
  '/history': <ClipboardList className="w-4 h-4" />,
  '/payment': <CreditCard className="w-4 h-4" />,
};

export default function Header() {
  const { status, profile, signOut } = useAuth();
  const isLoading = status === 'loading';
  const isAuthenticated = status === 'authenticated' && !!profile;

  return (
    <header className="fixed top-0 w-full flex justify-between items-center px-6 md:px-12 h-16 bg-surface-container-lowest/80 backdrop-blur-md border-b border-outline-variant/30 z-50">
      <Link to="/" aria-label="MockWise — Trang chủ" className="flex items-center">
        <Logo className="h-12 w-auto" />
      </Link>

      {/* Hide nav while we're still confirming the session — rendering a guest
          nav and then swapping to the user nav (or vice versa) causes a visible
          flash on every refresh. */}
      <nav className="hidden md:flex gap-8 items-center">
        {!isLoading &&
          (isAuthenticated ? HEADER_NAV_USER : HEADER_NAV_GUEST).map((item) => (
            <NavLink key={item.label} item={item} />
          ))}
      </nav>

      <div className="flex items-center gap-2 md:gap-4">
        {isLoading ? (
          <AuthSlotSkeleton />
        ) : isAuthenticated ? (
          <UserMenu fullName={profile.fullName} onSignOut={signOut} />
        ) : (
          <>
            <Link
              to="/login"
              className="text-sm font-semibold text-on-surface-variant hover:text-on-surface px-3 py-2 transition-colors"
            >
              Đăng nhập
            </Link>
            <Link
              to="/register"
              className="text-sm font-semibold bg-secondary text-on-secondary px-4 py-2 rounded-xl hover:opacity-90 transition-opacity shadow-sm shadow-secondary/20"
            >
              Đăng ký miễn phí
            </Link>
          </>
        )}
      </div>
    </header>
  );
}

// Reserves the same width as the login/register buttons (≈ 220px) so the
// header layout doesn't shift when the auth state resolves.
function AuthSlotSkeleton() {
  return (
    <div
      className="h-9 w-44 rounded-xl bg-surface-container-low animate-pulse"
      aria-hidden="true"
    />
  );
}

// Anchor links inside the homepage (e.g. /#pricing) need a plain <a> so the
// browser scrolls to the section. Pure paths (e.g. /practice) use react-router's
// <Link> for client-side navigation without a full page reload.
function NavLink({ item }: { item: NavItem }) {
  const className =
    'text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors';

  if (item.href.includes('#')) {
    return <a href={item.href} className={className}>{item.label}</a>;
  }
  return <Link to={item.href} className={className}>{item.label}</Link>;
}

type UserMenuProps = {
  fullName: string;
  onSignOut: () => Promise<void>;
};

function UserMenu({ fullName, onSignOut }: UserMenuProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const initial = fullName.trim().charAt(0).toUpperCase() || '?';

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  const handleSignOut = async () => {
    setOpen(false);
    await onSignOut();
    navigate('/', { replace: true });
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 pl-1 pr-2 py-1 rounded-full hover:bg-surface-container-low transition-colors"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <div className="w-8 h-8 rounded-full bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center text-sm font-bold">
          {initial}
        </div>
        <span className="hidden md:inline text-sm font-semibold text-on-surface max-w-[8rem] truncate">
          {fullName}
        </span>
        <ChevronDown className="w-4 h-4 text-on-surface-variant" />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-56 bg-surface-container-lowest border border-outline-variant rounded-xl shadow-lg overflow-hidden"
        >
          {USER_MENU_ITEMS.map((item) => (
            <Link
              key={item.href}
              to={item.href}
              role="menuitem"
              onClick={() => setOpen(false)}
              className="flex items-center gap-3 px-4 py-3 text-sm text-on-surface hover:bg-surface-container-low transition-colors"
            >
              {USER_MENU_ICONS[item.href]}
              {item.label}
            </Link>
          ))}
          <button
            type="button"
            role="menuitem"
            onClick={handleSignOut}
            className="w-full flex items-center gap-3 px-4 py-3 text-sm text-on-surface hover:bg-surface-container-low transition-colors border-t border-outline-variant/40"
          >
            <LogOut className="w-4 h-4" />
            Đăng xuất
          </button>
        </div>
      )}
    </div>
  );
}
