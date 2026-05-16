import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';

type Props = { children: ReactNode };

export default function ProtectedRoute({ children }: Props) {
  const { status, role } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="min-h-screen flex items-center justify-center text-on-surface-variant">
        Đang tải...
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // Admins live entirely in the /admin console — there's no practice/history/
  // payment surface for them. Bounce any user-facing route to the dashboard.
  // (`role` is briefly null after a hard refresh; only redirect once it has
  // actually resolved to ADMIN.)
  if (role === 'ADMIN') {
    return <Navigate to="/admin" replace />;
  }

  return <>{children}</>;
}
