# Dukkan web preview (visual QA only)

A **dev-only** react-native-web build of `apps/mobile`, used to take real screenshots of the
app in headless Chromium against the local backend when no emulator or SDK is available.
It is **not a product web app**: nothing here ships, and `apps/mobile` gains no web dependencies.

The app code is bundled unchanged from `apps/mobile/index.js` and resolved from
`apps/mobile/node_modules`. Only `react-native` itself and native-only modules are swapped:

| Module | Web stand-in (`web/stubs`) |
|---|---|
| `react-native` | `web/react-native.js`: react-native-web 0.21 plus no-op `TurboModuleRegistry` and a `KeyboardAvoidingView` layout fix (see below) |
| `react-native-maps` | Labelled "Map preview" placeholder that lists markers (pressable) |
| `react-native-keychain`, `@react-native-async-storage/async-storage` | `localStorage` |
| `@react-native-community/geolocation` | `navigator.geolocation` (Playwright pins it to St. Georg) |
| `@react-native-community/netinfo` | `navigator.onLine` |
| `react-native-localize` | `navigator.languages` |
| `@react-native/assets-registry/registry` | Empty registry (needed by react-native-svg's web build) |

`app.json`'s `apiBaseUrl` is replaced with `<origin>/api/v1`; Vite proxies `/api` to the backend
(`DUKKAN_API_TARGET`, default `http://localhost:8080`) because the backend sends no CORS headers.
The bundled Inter and IBM Plex Sans Arabic TTFs are loaded via `@font-face` under the same
family names the app uses.

## Run

```sh
. /opt/tools/env.sh                 # Node 24
cd tools/web-preview
npm ci
npm run build                       # → dist/
npm run screenshots                 # starts `vite preview` on :5199 if needed, writes screenshots/
ONLY=de-light npm run screenshots   # one variant (de-light, ar-light, de-dark)
npm run serve                       # browse the preview at http://localhost:5199 yourself
npm run dev                         # build + serve in one step
```

There is no Vite dev server: its dependency pre-bundling (esbuild) cannot read the Flow types in
`react-native/Libraries/*`, so `dev` builds first and then serves `dist/`.

```sh
```

Chromium comes from `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers` (Playwright 1.56.1 matches
revision 1194). Never run `playwright install` here.

`screenshot.mjs` uses a 390×844 viewport at deviceScaleFactor 2, forces the theme through
`colorScheme` emulation and the language by pre-seeding the stored language. Signed-in flows
pre-seed a token obtained from `/auth/login` for the demo accounts; the script also clears the
customer's Anatolia cart and adds two product favourites before running. With the cart
still empty it captures `06-fulfilment-empty-cart` (slots read-only, because the server answers
`CART_EMPTY`), then adds three products; afterwards the demo customer keeps that cart and the
favourites. Files are written as
`screenshots/<lang>-<theme>-<nn>-<name>.png` (git-ignored).

## Known differences from native

- **Maps** are a placeholder list, not a map.
- **Text alignment in RTL:** react-native-web renders `Text` with `dir="auto"`, so a Latin-only
  string (market names, addresses, "1 kg", typed search text) aligns left in the Arabic UI.
  On iOS/Android `textAlign: 'auto'` follows the layout direction and would align right.
- **Safe-area insets** are zero (no notch, no home indicator), so tab bars and sheets sit flush
  with the screen edge.
- **Switch** uses react-native-web's control (teal default thumb when on, a CSS shadow).
- **Modals** are DOM overlays; slide animation, haptics, the system back gesture, native alerts
  (`Alert.alert` is a no-op on web) and the keyboard are not represented.
- **Layout compensations** in the harness (not app changes): `KeyboardAvoidingView` gets full
  height so `Sheet`'s `maxHeight: '88%'` resolves against the screen as in Yoga (CSS resolves it
  against the content height and clipped every sheet); text inputs get `min-width: 0` (Yoga has
  no intrinsic input width; without it, inputs overflowed their field in RTL); browser focus
  rings on inputs are hidden.
- Seed data has no product, market or banner images, so every tile shows the placeholder icon.
