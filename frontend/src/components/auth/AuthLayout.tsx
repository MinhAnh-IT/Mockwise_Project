import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import Logo from '@/components/ui/Logo';
import { SITE } from '@/data/site';

type Props = {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
};

export default function AuthLayout({ title, subtitle, children, footer }: Props) {
  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <header className="px-6 md:px-12 h-16 flex items-center">
        <Link to="/" aria-label={`${SITE.name} — Trang chủ`} className="flex items-center">
          <Logo className="h-9 w-auto" />
        </Link>
      </header>

      <main className="flex-1 flex items-center justify-center px-6 py-10">
        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-on-surface mb-2">{title}</h1>
            {subtitle && <p className="text-on-surface-variant text-sm">{subtitle}</p>}
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 md:p-8 shadow-sm">
            {children}
          </div>

          {footer && (
            <div className="mt-6 text-center text-sm text-on-surface-variant">{footer}</div>
          )}
        </div>
      </main>
    </div>
  );
}
