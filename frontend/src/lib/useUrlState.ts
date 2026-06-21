import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

export type UrlValue = string | number | boolean | undefined | null;

/**
 * Mirror a small bag of UI state — active tab, filters, page index — to the URL
 * query string so it survives a page refresh and is shareable/bookmarkable.
 *
 * The URL is the single source of truth: `state` is derived from the current
 * query string (falling back to `defaults`), and `setState` patches the query.
 * Values equal to their default — or empty/undefined/null — are dropped so the
 * URL stays clean (e.g. no `?status=` when nothing is selected). Writes use
 * `replace` so adjusting a filter doesn't spam the browser history.
 *
 * `defaults` MUST be a stable reference (declare it as a module-level constant),
 * otherwise the derived `state` / `setState` identities change every render and
 * can retrigger data-loading effects.
 */
export function useUrlState<T extends Record<string, UrlValue>>(defaults: T) {
  const [params, setParams] = useSearchParams();

  const state = useMemo(() => {
    const out = { ...defaults };
    for (const key of Object.keys(defaults) as (keyof T)[]) {
      const raw = params.get(String(key));
      if (raw === null) continue;
      const def = defaults[key];
      if (typeof def === 'number') {
        out[key] = (Number.isNaN(Number(raw)) ? def : Number(raw)) as T[keyof T];
      } else if (typeof def === 'boolean') {
        out[key] = (raw === 'true') as T[keyof T];
      } else {
        out[key] = raw as T[keyof T];
      }
    }
    return out;
  }, [params, defaults]);

  const setState = useCallback(
    (patch: Partial<T>) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [k, v] of Object.entries(patch)) {
            const def = defaults[k as keyof T];
            if (v === undefined || v === null || v === '' || v === def) {
              next.delete(k);
            } else {
              next.set(k, String(v));
            }
          }
          return next;
        },
        { replace: true },
      );
    },
    [setParams, defaults],
  );

  return [state, setState] as const;
}
