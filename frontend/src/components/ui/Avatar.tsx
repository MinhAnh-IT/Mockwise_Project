import { useState } from 'react';

type Props = {
  src?: string | null;
  fullName: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
};

const SIZE_CLASS: Record<NonNullable<Props['size']>, string> = {
  sm: 'w-8 h-8 text-sm',
  md: 'w-12 h-12 text-lg',
  lg: 'w-20 h-20 text-3xl',
};

/**
 * Renders the user's avatar image with an initials fallback. Falls back to
 * initials whenever:
 *  - `src` is null/empty (no avatar uploaded), or
 *  - the image fails to load (presigned URL expired, network error).
 */
export default function Avatar({ src, fullName, size = 'md', className = '' }: Props) {
  const [failed, setFailed] = useState(false);
  const initial = fullName.trim().charAt(0).toUpperCase() || '?';
  const showImage = src && !failed;

  const baseClass = `${SIZE_CLASS[size]} rounded-full overflow-hidden flex-shrink-0 ${className}`;

  if (showImage) {
    return (
      <img
        src={src}
        alt={fullName}
        onError={() => setFailed(true)}
        className={`${baseClass} object-cover bg-surface-container-low`}
        referrerPolicy="no-referrer"
      />
    );
  }

  return (
    <div
      aria-label={fullName}
      className={`${baseClass} bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center font-bold`}
    >
      {initial}
    </div>
  );
}
