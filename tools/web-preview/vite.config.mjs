// Dev-only web preview of apps/mobile (react-native-web). Visual QA only, not a product web app.
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { defineConfig } from 'vite';

const here = path.dirname(fileURLToPath(import.meta.url));
const app = path.resolve(here, '../../apps/mobile');
const appModules = path.join(app, 'node_modules');
const ownModules = path.join(here, 'node_modules');
const stub = (name) => path.join(here, 'web/stubs', name);

const API_TARGET = process.env.DUKKAN_API_TARGET ?? 'http://localhost:8080';
const APP_JSON_ID = '\0dukkan-app-json';

/**
 * The app reads its API base URL from app.json. The backend sends no CORS headers, so the
 * preview calls the same origin (/api/v1) and Vite proxies it to the backend.
 */
function appJsonOverride() {
  return {
    name: 'dukkan-app-json',
    enforce: 'pre',
    resolveId(source, importer) {
      if (source.endsWith('app.json') && importer && path.resolve(path.dirname(importer), source) === path.join(app, 'app.json')) {
        return APP_JSON_ID;
      }
      return null;
    },
    load(id) {
      if (id === APP_JSON_ID) {
        return `export default { name: 'Dukkan', displayName: 'Dukkan', apiBaseUrl: window.location.origin + '/api/v1' };\nexport const name = 'Dukkan';`;
      }
      return null;
    },
  };
}

export default defineConfig({
  root: here,
  plugins: [appJsonOverride()],
  define: {
    __DEV__: 'false',
    global: 'globalThis',
  },
  resolve: {
    alias: [
      // One React for app code, navigation libs and react-native-web.
      { find: /^react$/, replacement: path.join(appModules, 'react') },
      { find: /^react\/(.*)$/, replacement: path.join(appModules, 'react/$1') },
      { find: /^react-dom$/, replacement: path.join(ownModules, 'react-dom') },
      { find: /^react-dom\/(.*)$/, replacement: path.join(ownModules, 'react-dom/$1') },
      { find: /^react-native$/, replacement: path.join(here, 'web/react-native.js') },
      // Native-only modules → web stand-ins.
      { find: /^react-native-maps$/, replacement: stub('maps.jsx') },
      { find: /^react-native-keychain$/, replacement: stub('keychain.js') },
      { find: /^@react-native-async-storage\/async-storage$/, replacement: stub('async-storage.js') },
      { find: /^@react-native-community\/geolocation$/, replacement: stub('geolocation.js') },
      { find: /^@react-native-community\/netinfo$/, replacement: stub('netinfo.js') },
      { find: /^react-native-localize$/, replacement: stub('localize.js') },
      { find: /^@react-native\/assets-registry\/registry$/, replacement: stub('assets-registry.js') },
    ],
    extensions: ['.web.tsx', '.web.ts', '.web.mjs', '.web.jsx', '.web.js', '.tsx', '.ts', '.mjs', '.jsx', '.js', '.json'],
    dedupe: ['react', 'react-dom'],
  },
  esbuild: { jsx: 'automatic' },
  build: {
    rollupOptions: {
      onwarn(warning, warn) {
        if (warning.code === 'MODULE_LEVEL_DIRECTIVE' || warning.code === 'SOURCEMAP_ERROR') return;
        warn(warning);
      },
    },
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
    chunkSizeWarningLimit: 4000,
    commonjsOptions: { transformMixedEsModules: true },
  },
  server: { fs: { allow: [here, app] }, proxy: { '/api': API_TARGET } },
  preview: { proxy: { '/api': API_TARGET } },
});
