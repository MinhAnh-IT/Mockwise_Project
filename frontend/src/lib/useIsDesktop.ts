import { useEffect, useState } from 'react';

// Matches Tailwind's `md` breakpoint (768px). Used by the coding workspaces to
// switch between the desktop split-pane and the mobile stacked-tab layout. The
// resizable split-pane (mouse-drag dividers, inline % widths) only makes sense
// with a pointer + wide viewport, so below this we render one panel at a time.
const DESKTOP_QUERY = '(min-width: 768px)';

export default function useIsDesktop(): boolean {
  const [isDesktop, setIsDesktop] = useState<boolean>(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return true;
    return window.matchMedia(DESKTOP_QUERY).matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia(DESKTOP_QUERY);
    const onChange = (e: MediaQueryListEvent) => setIsDesktop(e.matches);
    setIsDesktop(mql.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return isDesktop;
}
