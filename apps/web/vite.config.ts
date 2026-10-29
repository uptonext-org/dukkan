/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The portal talks to the backend on the same origin (/api). In development Vite proxies it to
// the local backend; in production the ingress routes /api to the backend and the rest here.
declare const process: { env: Record<string, string | undefined> };
const backend = process.env.DUKKAN_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: { port: 5174, strictPort: true, proxy: { '/api': backend } },
  preview: { proxy: { '/api': backend } },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    restoreMocks: true,
  },
});
