/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The portal talks to the backend on the same origin (/api). In development Vite proxies it to
// the local backend; in production the ingress routes /api to the backend and the rest here.
declare const process: { env: Record<string, string | undefined> };
const backend = process.env.DUKKAN_BACKEND_URL ?? 'http://localhost:8080';
// Static hosting without a proxy (e.g. Hostinger): the API's absolute URL, fixed at build time.
// Empty: same origin (/api/v1). The backend must list this portal in DUKKAN_CORS_ALLOWED_ORIGINS.
const apiBaseUrl = process.env.DUKKAN_API_BASE_URL ?? '';

export default defineConfig({
  plugins: [react()],
  base: './',
  define: { __DUKKAN_API_BASE_URL__: JSON.stringify(apiBaseUrl) },
  server: { port: 5174, strictPort: true, proxy: { '/api': backend } },
  preview: { proxy: { '/api': backend } },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    restoreMocks: true,
  },
});
