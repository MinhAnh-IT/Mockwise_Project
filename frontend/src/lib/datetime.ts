/**
 * Backend services run in UTC and store timestamps as UTC wall-clock, but they
 * serialise `LocalDateTime` as a naive ISO string with no offset
 * (e.g. "2026-06-21T14:55:37.401"). Passed straight to `new Date(...)`, the
 * browser would read that as the viewer's *local* time — showing the wrong hour
 * for anyone outside UTC (e.g. 7h off in Vietnam).
 *
 * `parseServerDate` treats an offset-less timestamp as UTC, so the resulting
 * `Date` is the correct instant and our `Intl.DateTimeFormat` formatters (which
 * use the runtime's local zone) render it in each user's own timezone.
 *
 * Strings that already carry an offset/Z, and date-only values, are left as-is.
 */
const NAIVE_DATETIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?(\.\d+)?$/;

export function parseServerDate(value: string): Date {
  return new Date(NAIVE_DATETIME.test(value) ? `${value}Z` : value);
}
