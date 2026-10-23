/// <reference types="vitest/config" />
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig } from 'vite';

// The customer app (apps/mobile) for the browser. App code and its libraries come from
// apps/mobile/node_modules (run `npm ci` there first); react-native and native-only modules are
// swapped for react-native-web and the stand-ins in src/shims. The browser services themselves
// (storage, photos, speech, video frames, location) are injected in src/dependencies.ts.
declare const process: { env: Record<string, string | undefined> };

const here = path.dirname(fileURLToPath(import.meta.url));
const app = path.resolve(here, '../mobile');
const appModules = path.join(app, 'node_modules');
const ownModules = path.join(here, 'node_modules');
const shim = (name: string) => path.join(here, 'src/shims', name);
const backend = process.env.DUKKAN_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  root: here,
  define: {
    __DEV__: 'false',
    global: 'globalThis',
  },
  resolve: {
    alias: [
      // One React for the app, navigation libraries and react-native-web.
      { find: /^react$/, replacement: path.join(appModules, 'react') },
      { find: /^react\/(.*)$/, replacement: path.join(appModules, 'react/$1') },
      { find: /^react-dom$/, replacement: path.join(ownModules, 'react-dom') },
      { find: /^react-dom\/(.*)$/, replacement: path.join(ownModules, 'react-dom/$1') },
      { find: /^react-native$/, replacement: shim('react-native.ts') },
      { find: /^react-native-maps$/, replacement: shim('maps.tsx') },
      { find: /^@react-native-community\/netinfo$/, replacement: shim('netinfo.ts') },
      { find: /^react-native-localize$/, replacement: shim('localize.ts') },
      { find: /^(react-native-keychain|react-native-image-picker|dukkan-capture)$/, replacement: shim('unavailable.ts') },
      { find: /^@react-native-community\/geolocation$/, replacement: shim('unavailable.ts') },
      { find: /^@react-native-async-storage\/async-storage$/, replacement: shim('unavailable.ts') },
      // react-native-svg's web build asks the asset registry for nothing the app uses.
      { find: /^@react-native\/assets-registry\/registry$/, replacement: shim('assets-registry.ts') },
    ],
    extensions: ['.web.tsx', '.web.ts', '.web.mjs', '.web.jsx', '.web.js', '.tsx', '.ts', '.mjs', '.jsx', '.js', '.json'],
    dedupe: ['react', 'react-dom'],
  },
  oxc: { jsx: { runtime: 'automatic' } },
  build: {
    rollupOptions: {
      onwarn(warning, warn) {
        // 'use client' in react-native-screens and source maps of prebuilt libraries.
        if (warning.code === 'MODULE_LEVEL_DIRECTIVE' || warning.code === 'SOURCEMAP_ERROR') {
          return;
        }
        warn(warning);
      },
    },
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: 'hidden',
    chunkSizeWarningLimit: 4000,
    commonjsOptions: { transformMixedEsModules: true },
  },
  server: { fs: { allow: [here, app] }, proxy: { '/api': backend } },
  preview: { proxy: { '/api': backend } },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    restoreMocks: true,
  },
});
