/**
 * Light/dark class tokens for the coding workspace. The workspace defaults
 * to LIGHT; the user flips to dark with the toolbar toggle (persisted in
 * localStorage). Keeping the class strings in one place keeps the four
 * components consistent and readable.
 */
export type CwAppearance = 'light' | 'dark';

export const CW_THEME_STORAGE_KEY = 'mockwise-cw-appearance';

export function readStoredAppearance(): CwAppearance {
  if (typeof window === 'undefined') return 'light';
  return window.localStorage.getItem(CW_THEME_STORAGE_KEY) === 'dark'
    ? 'dark'
    : 'light';
}

export function cwTokens(dark: boolean) {
  return {
    dark,
    /** Monaco editor theme name registered in CodeEditor. */
    monaco: dark ? 'mockwise-dark' : 'mockwise-light',

    // Shells
    workspace: dark
      ? 'bg-zinc-900 text-zinc-100'
      : 'bg-zinc-50 text-zinc-900',
    surface: dark ? 'bg-zinc-950' : 'bg-white',
    panel: dark ? 'bg-zinc-950' : 'bg-white',
    border: dark ? 'border-zinc-800' : 'border-zinc-200',
    divider: dark
      ? 'bg-zinc-800 hover:bg-sky-600'
      : 'bg-zinc-200 hover:bg-sky-500',

    // Text
    textStrong: dark ? 'text-zinc-100' : 'text-zinc-900',
    textBody: dark ? 'text-zinc-300' : 'text-zinc-700',
    textMuted: dark ? 'text-zinc-500' : 'text-zinc-500',
    headingSub: dark ? 'text-zinc-200' : 'text-zinc-800',

    // Bits
    chip: dark ? 'bg-zinc-800/80 text-zinc-300' : 'bg-zinc-100 text-zinc-700',
    sigBox: dark
      ? 'border-zinc-800 bg-zinc-900/60'
      : 'border-zinc-200 bg-zinc-50',
    sigCode: dark ? 'text-sky-300' : 'text-sky-700',
    codeInline: dark
      ? 'bg-zinc-800 text-amber-300'
      : 'bg-zinc-100 text-amber-700',
    codeBlock: dark ? 'bg-zinc-900 text-zinc-200' : 'bg-zinc-100 text-zinc-800',
    card: dark
      ? 'border-zinc-800 bg-zinc-900/50'
      : 'border-zinc-200 bg-zinc-50',

    // Controls
    select: dark
      ? 'border-zinc-700 bg-zinc-800 text-zinc-200'
      : 'border-zinc-300 bg-white text-zinc-800',
    btnGhost: dark
      ? 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
      : 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-900',
    btnSecondary: dark
      ? 'border-zinc-700 bg-zinc-800 text-zinc-100 hover:bg-zinc-700'
      : 'border-zinc-300 bg-white text-zinc-800 hover:bg-zinc-100',
    timerIdle: dark ? 'bg-zinc-800 text-zinc-200' : 'bg-zinc-100 text-zinc-700',

    // Tabs / pills (TestcasePanel)
    tabActive: dark
      ? 'border-sky-400 text-zinc-100'
      : 'border-sky-500 text-zinc-900',
    tabIdle: dark
      ? 'border-transparent text-zinc-500 hover:text-zinc-300'
      : 'border-transparent text-zinc-500 hover:text-zinc-800',
    pill: dark
      ? 'bg-zinc-800/60 text-zinc-400 hover:text-zinc-200'
      : 'bg-zinc-100 text-zinc-600 hover:text-zinc-900',
    pillActive: dark ? 'bg-zinc-700 text-zinc-100' : 'bg-zinc-200 text-zinc-900',

    // Status accents (slightly stronger text on light bg)
    ok: dark ? 'text-emerald-400' : 'text-emerald-600',
    bad: dark ? 'text-rose-400' : 'text-rose-600',
    kvValue: dark ? 'text-zinc-200' : 'text-zinc-800',
    kvMuted: dark ? 'text-zinc-400' : 'text-zinc-500',
  };
}

export type CwTokens = ReturnType<typeof cwTokens>;

/** Difficulty badge palette, theme-aware. */
export function difficultyStyle(dark: boolean) {
  return {
    EASY: {
      label: 'Easy',
      cls: dark
        ? 'bg-emerald-500/15 text-emerald-400'
        : 'bg-emerald-100 text-emerald-700',
    },
    MEDIUM: {
      label: 'Medium',
      cls: dark
        ? 'bg-amber-500/15 text-amber-400'
        : 'bg-amber-100 text-amber-700',
    },
    HARD: {
      label: 'Hard',
      cls: dark ? 'bg-rose-500/15 text-rose-400' : 'bg-rose-100 text-rose-700',
    },
  };
}
