import type { ReactNode } from 'react';

type Props = {
  icon: ReactNode;
  label: string;
  href?: string;
};

export default function SocialIcon({ icon, label, href }: Props) {
  const className =
    'w-10 h-10 flex items-center justify-center rounded-full bg-surface-container-high text-on-surface-variant hover:text-secondary hover:bg-secondary-fixed transition-all';

  if (href) {
    return (
      <a href={href} aria-label={label} className={className}>
        {icon}
      </a>
    );
  }

  return (
    <button type="button" aria-label={label} className={className}>
      {icon}
    </button>
  );
}
