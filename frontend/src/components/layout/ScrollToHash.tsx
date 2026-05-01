import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

// React Router only updates the URL — it doesn't natively scroll to a
// `#section` anchor when the hash changes. This component watches the
// location and scrolls the element whose id matches the hash into view.
//
// Triggers in two cases:
// - Navigation to a hashed URL from a different route (e.g. /practice → /#pricing)
// - Click on a hash link while already on that route (hash changes, pathname same)
export default function ScrollToHash() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (!hash) {
      // No hash → scroll to top so route changes feel like fresh pages.
      window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
      return;
    }

    // Section may not be mounted yet on a fresh route change. Defer one frame
    // so the route's components have rendered before we look up the element.
    const id = hash.slice(1);
    const tryScroll = () => {
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    };

    requestAnimationFrame(tryScroll);
  }, [pathname, hash]);

  return null;
}
