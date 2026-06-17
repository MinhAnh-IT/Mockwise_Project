import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const projectRoot = path.dirname(fileURLToPath(import.meta.url));

// Unit-test config kept separate from vite.config.ts so the dev/build pipeline
// stays untouched. jsdom gives the realtime transcriber a `window` to attach a
// fake SpeechRecognition to.
export default defineConfig({
  resolve: {
    alias: { '@': path.resolve(projectRoot, './src') },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
});
