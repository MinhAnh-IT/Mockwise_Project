import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '@/auth/useAuth';

type Props = { children: ReactNode };

/**
 * Gate for admin-only pages. This is a UX guard only — the API gateway is
 * the real authorization boundary (it re-introspects the JWT and 403s any
 * `/admin/` path for non-admins). We still render a clear "no access" screen
 * so a non-admin who deep-links here isn't left staring at failed requests.
 *
 * `role` is decoded from the JWT and is briefly `null` right after a hard
 * refresh (cached-profile optimistic render) until the token resolves — we
 * treat that window as "still verifying" rather than denying.
 */
export default function AdminRoute({ children }: Props) {
  const { status, role } = useAuth();
  const location = useLocation();

  if (status === 'loading' || (status === 'authenticated' && role === null)) {
    return (
      <div className="min-h-screen flex items-center justify-center text-on-surface-variant">
        Đang kiểm tra quyền truy cập...
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (role !== 'ADMIN') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4 px-6 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 text-red-600">
          <ShieldAlert className="h-7 w-7" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-on-surface">
            Không có quyền truy cập
          </h1>
          <p className="mt-1 max-w-sm text-sm text-on-surface-variant">
            Khu vực quản trị ngân hàng câu hỏi chỉ dành cho tài khoản quản trị
            viên.
          </p>
        </div>
        <Link
          to="/"
          className="rounded-xl bg-secondary px-4 py-2 text-sm font-semibold text-on-secondary transition-opacity hover:opacity-90"
        >
          Về trang chủ
        </Link>
      </div>
    );
  }

  return <>{children}</>;
}
