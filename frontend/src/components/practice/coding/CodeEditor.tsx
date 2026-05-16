import { useEffect, useRef } from 'react';
import Editor, { loader, type OnMount } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { LANGUAGE_META, type CodingLanguage } from '@/types/coding';

// Bundle Monaco from node_modules instead of the default jsdelivr CDN —
// the app runs behind nginx with no guaranteed outbound internet, and a
// CDN miss would leave the editor blank.
loader.config({ monaco });

// LeetCode-ish dark theme. Registered once, lazily, on first mount.
let themeRegistered = false;
function ensureTheme(m: typeof monaco) {
  if (themeRegistered) return;
  m.editor.defineTheme('mockwise-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '6a9955', fontStyle: 'italic' },
      { token: 'keyword', foreground: 'c586c0' },
      { token: 'string', foreground: 'ce9178' },
      { token: 'number', foreground: 'b5cea8' },
      { token: 'type', foreground: '4ec9b0' },
    ],
    colors: {
      'editor.background': '#1e1e1e',
      'editor.lineHighlightBackground': '#2a2d2e',
      'editorLineNumber.foreground': '#5a5a5a',
      'editorLineNumber.activeForeground': '#c6c6c6',
      'editorCursor.foreground': '#ffcc00',
      'editor.selectionBackground': '#264f78',
      // Subtle box around the bracket matching the caret position (just the
      // two bracket glyphs — not a full open↔close line).
      'editorBracketMatch.background': '#ffcc0022',
      'editorBracketMatch.border': '#ffcc0066',
    },
  });
  // Default appearance — light, like a fresh editor on white.
  m.editor.defineTheme('mockwise-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '008000', fontStyle: 'italic' },
      { token: 'keyword', foreground: '7c3aed' },
      { token: 'string', foreground: 'a31515' },
      { token: 'number', foreground: '098658' },
      { token: 'type', foreground: '267f99' },
    ],
    colors: {
      'editor.background': '#ffffff',
      'editor.lineHighlightBackground': '#f3f4f6',
      'editorLineNumber.foreground': '#9ca3af',
      'editorLineNumber.activeForeground': '#374151',
      'editorCursor.foreground': '#1f2937',
      'editor.selectionBackground': '#bfdbfe',
      'editorBracketMatch.background': '#1d4ed81a',
      'editorBracketMatch.border': '#1d4ed866',
    },
  });
  themeRegistered = true;
}

type Props = {
  value: string;
  language: CodingLanguage;
  onChange: (next: string) => void;
  /** Ctrl/Cmd+Enter — wired through a ref so the action stays current. */
  onRun: () => void;
  /** Ctrl/Cmd+Shift+Enter. */
  onSubmit: () => void;
  readOnly?: boolean;
  /** 'light' (default) or 'dark' — switched live by the workspace toggle. */
  appearance: 'light' | 'dark';
};

/**
 * Monaco-backed editor with a LeetCode-equivalent keymap. Most shortcuts
 * (comment toggle Ctrl+/, move line Alt+↑↓, dup line Shift+Alt+↓,
 * multi-cursor Ctrl+D, find Ctrl+F, command palette F1) come for free from
 * Monaco's VS Code keybindings — what people mean by "LeetCode shortcuts"
 * is exactly this keymap. We only add Run / Submit and a Format action.
 */
export default function CodeEditor({
  value,
  language,
  onChange,
  onRun,
  onSubmit,
  readOnly = false,
  appearance,
}: Props) {
  const themeName = appearance === 'dark' ? 'mockwise-dark' : 'mockwise-light';
  // Keep the latest callbacks reachable from the once-registered Monaco
  // actions without re-registering them on every render.
  const runRef = useRef(onRun);
  const submitRef = useRef(onSubmit);
  useEffect(() => {
    runRef.current = onRun;
    submitRef.current = onSubmit;
  }, [onRun, onSubmit]);

  const handleMount: OnMount = (editor, m) => {
    ensureTheme(m);
    m.editor.setTheme(themeName);

    editor.addAction({
      id: 'mockwise.run',
      label: 'Chạy code (sample tests)',
      keybindings: [m.KeyMod.CtrlCmd | m.KeyCode.Enter],
      run: () => runRef.current(),
    });
    editor.addAction({
      id: 'mockwise.submit',
      label: 'Nộp bài',
      keybindings: [m.KeyMod.CtrlCmd | m.KeyMod.Shift | m.KeyCode.Enter],
      run: () => submitRef.current(),
    });
  };

  return (
    <Editor
      className="h-full"
      theme={themeName}
      beforeMount={ensureTheme}
      language={LANGUAGE_META[language].monaco}
      value={value}
      onChange={(next) => onChange(next ?? '')}
      onMount={handleMount}
      loading={
        <div className="h-full grid place-items-center text-xs text-zinc-500">
          Đang tải trình soạn thảo…
        </div>
      }
      options={{
        readOnly,
        fontSize: 14,
        fontFamily:
          "'JetBrains Mono','Fira Code',ui-monospace,SFMono-Regular,Menlo,monospace",
        fontLigatures: true,
        lineNumbers: 'on',
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        smoothScrolling: true,
        cursorBlinking: 'smooth',
        cursorSmoothCaretAnimation: 'on',
        tabSize: 4,
        insertSpaces: true,
        automaticLayout: true,
        renderLineHighlight: 'all',
        matchBrackets: 'always',
        bracketPairColorization: { enabled: true },
        guides: {
          // No vertical open↔close bracket line at all — user found it noisy.
          bracketPairs: false,
          highlightActiveBracketPair: false,
          indentation: true,
        },
        autoClosingBrackets: 'languageDefined',
        autoClosingQuotes: 'languageDefined',
        formatOnPaste: true,
        padding: { top: 14, bottom: 14 },
        scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
        stickyScroll: { enabled: false },
        suggestSelection: 'first',
        fixedOverflowWidgets: true,
      }}
    />
  );
}
