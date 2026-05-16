import { Fragment, type ReactNode } from 'react';

/**
 * Intentionally tiny markdown renderer — the problem `description` is the
 * only markdown we show and pulling a full parser (+ sanitiser) for it
 * isn't worth the bundle. Supports: #/##/### headings, fenced ``` blocks,
 * `-`/`*` bullet lists, blank-line paragraphs, and inline **bold** /
 * `code`. Everything is rendered as React nodes (no dangerouslySetInnerHTML)
 * so there's no XSS surface.
 */
export default function Markdown({
  source,
  dark,
}: {
  source: string;
  dark: boolean;
}) {
  const c = {
    h1: dark ? 'text-zinc-100' : 'text-zinc-900',
    h3: dark ? 'text-zinc-200' : 'text-zinc-800',
    body: dark ? 'text-zinc-300' : 'text-zinc-700',
    pre: dark ? 'bg-zinc-900 text-zinc-200' : 'bg-zinc-100 text-zinc-800',
    code: dark ? 'bg-zinc-800 text-amber-300' : 'bg-zinc-100 text-amber-700',
    strong: dark ? 'text-zinc-100' : 'text-zinc-900',
  };
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const blocks: ReactNode[] = [];
  let i = 0;
  let key = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Fenced code block
    if (line.trimStart().startsWith('```')) {
      const buf: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trimStart().startsWith('```')) {
        buf.push(lines[i]);
        i++;
      }
      i++; // closing fence
      blocks.push(
        <pre
          key={key++}
          className={`my-3 overflow-x-auto rounded-lg px-3 py-2.5 text-[13px] leading-relaxed ${c.pre}`}
        >
          <code>{buf.join('\n')}</code>
        </pre>,
      );
      continue;
    }

    // Headings
    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      const cls =
        level === 1
          ? `text-lg font-bold ${c.h1} mt-4 mb-2`
          : level === 2
            ? `text-base font-bold ${c.h1} mt-4 mb-2`
            : `text-sm font-semibold ${c.h3} mt-3 mb-1.5`;
      blocks.push(
        <p key={key++} className={cls}>
          {renderInline(heading[2], c)}
        </p>,
      );
      i++;
      continue;
    }

    // Bullet list
    if (/^\s*[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*]\s+/, ''));
        i++;
      }
      blocks.push(
        <ul
          key={key++}
          className={`my-2 list-disc space-y-1 pl-5 text-sm ${c.body}`}
        >
          {items.map((it, idx) => (
            <li key={idx}>{renderInline(it, c)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    // Blank line → skip
    if (line.trim() === '') {
      i++;
      continue;
    }

    // Paragraph: gather until blank line
    const para: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !/^(#{1,3})\s+/.test(lines[i]) &&
      !/^\s*[-*]\s+/.test(lines[i]) &&
      !lines[i].trimStart().startsWith('```')
    ) {
      para.push(lines[i]);
      i++;
    }
    blocks.push(
      <p key={key++} className={`my-2 text-sm leading-relaxed ${c.body}`}>
        {renderInline(para.join('\n'), c)}
      </p>,
    );
  }

  return <div>{blocks}</div>;
}

type InlineColors = { code: string; strong: string };

/** Inline pass: **bold**, `code`, and \n → <br/>. */
function renderInline(text: string, c: InlineColors): ReactNode {
  const out: ReactNode[] = [];
  const re = /(`[^`]+`|\*\*[^*]+\*\*)/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let k = 0;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(withBreaks(text.slice(last, m.index), k++));
    const tok = m[0];
    if (tok.startsWith('`')) {
      out.push(
        <code
          key={k++}
          className={`rounded px-1.5 py-0.5 font-mono text-[13px] ${c.code}`}
        >
          {tok.slice(1, -1)}
        </code>,
      );
    } else {
      out.push(
        <strong key={k++} className={`font-semibold ${c.strong}`}>
          {tok.slice(2, -2)}
        </strong>,
      );
    }
    last = m.index + tok.length;
  }
  if (last < text.length) out.push(withBreaks(text.slice(last), k++));
  return out;
}

function withBreaks(text: string, key: number): ReactNode {
  const parts = text.split('\n');
  return (
    <Fragment key={key}>
      {parts.map((p, idx) => (
        <Fragment key={idx}>
          {idx > 0 && <br />}
          {p}
        </Fragment>
      ))}
    </Fragment>
  );
}
