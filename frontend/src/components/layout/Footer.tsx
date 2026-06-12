import { Link } from 'react-router-dom';
import SocialIcon from '@/components/ui/SocialIcon';
import { SITE } from '@/data/site';
import { SOCIAL_LINKS } from '@/data/social';

/**
 * Slim single-line footer: brand + copyright on the left, social icons on the
 * right. Collapses to a tidy two-row stack only on very narrow screens.
 */
export default function Footer() {
  return (
    <footer className="mt-auto border-t border-outline-variant/30 bg-surface-container/40">
      <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-6 py-4 sm:flex-row md:px-12">
        <p className="text-xs text-on-surface-variant">
          © 2024{' '}
          <Link
            to="/"
            className="font-semibold text-on-surface transition-colors hover:text-secondary"
          >
            {SITE.name}
          </Link>
          <span className="mx-1.5 text-outline-variant">·</span>
          {SITE.madeIn}
        </p>

        <div className="flex items-center gap-1">
          {SOCIAL_LINKS.map(({ label, href, Icon }) => (
            <SocialIcon
              key={label}
              label={label}
              href={href}
              icon={<Icon className="h-3.5 w-3.5" />}
            />
          ))}
        </div>
      </div>
    </footer>
  );
}
