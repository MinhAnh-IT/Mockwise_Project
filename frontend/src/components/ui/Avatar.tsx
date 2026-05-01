import { useEffect, useState } from 'react';
import { fetchAuthedBlobUrl } from '@/api/storage';

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
 * Renders the user's avatar image with an initials fallback.
 *
 * The avatar URL we get from the profile API is a same-origin path
 * (e.g. `/api/v1/storage/avatars/me?v=...`) that the storage service
 * gates with the same JWT as every other API call. Browsers don't send
 * Authorization headers on plain `<img src>` requests, so we fetch the
 * bytes ourselves with the bearer token and turn them into a blob URL.
 *
 * Falls back to initials when:
 *  - `src` is null/empty (no avatar uploaded), or
 *  - the fetch fails (URL expired, network error, 401, etc).
 */
export default function Avatar({ src, fullName, size = 'md', className = '' }: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const initial = fullName.trim().charAt(0).toUpperCase() || '?';

  useEffect(() => {
    setFailed(false);
    setBlobUrl(null);

    if (!src) return;

    let active = true;
    let createdUrl: string | null = null;

    fetchAuthedBlobUrl(src)
      .then((url) => {
        if (!active) {
          URL.revokeObjectURL(url);
          return;
        }
        createdUrl = url;
        setBlobUrl(url);
      })
      .catch(() => {
        if (active) setFailed(true);
      });

    // Revoke the object URL when the src changes or the component unmounts so
    // we don't leak browser memory for previous avatar fetches.
    return () => {
      active = false;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [src]);

  const baseClass = `${SIZE_CLASS[size]} rounded-full overflow-hidden flex-shrink-0 ${className}`;

  if (blobUrl && !failed) {
    return (
      <img
        src={blobUrl}
        alt={fullName}
        className={`${baseClass} object-cover bg-surface-container-low`}
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
