# Dukkan for the browser (customer web app)

The customer app of `apps/mobile`, unchanged, built for the browser with react-native-web. Every
screen of the app is here (markets, map, search, carts, checkout, orders, shopping list by voice,
photo or video, profile, merchant mode), in German, English and Arabic (right to left), light and dark.

Only the platform services differ. `src/dependencies.ts` injects browser versions of what the app
gets from native modules on iOS/Android:

| Service | Browser implementation (`src/platform`) | Notes |
|---|---|---|
| Session tokens | `localStorage` | Signed in across tabs and restarts. Access token 15 min, refresh token rotates; scripts limited by the CSP |
| Preferences (language, place) | `localStorage` | |
| Location | `navigator.geolocation` | Needs https |
| Photos | File chooser (`capture` opens the camera on phones), scaled to 2048 px and re-encoded as JPEG locally (drops EXIF/location) | HEIC only where the browser decodes it (Safari) |
| Dictation | Web Speech API (Chrome, Edge, Safari) | **The browser may send the audio to Google/Apple** for recognition; only the text reaches Dukkan. Must be in the privacy notice. Hidden where unsupported (Firefox) |
| Video frames | File chooser + `<video>`/canvas: N frames at 1280 px | The video stays in the browser |
| Map | Leaflet with raster tiles from `DUKKAN_MAP_TILE_URL` | Default OpenStreetMap tiles are for light use only, see below |
| Push | none | Notifications stay in the in-app inbox |
| Apple/Google sign-in | none (as in the app until B-39a) | |

`src/shims` adapts what react-native-web lacks: `Alert.alert` uses the browser's dialogs, `Share`
opens the share sheet or downloads the data export, `react-native-maps` is a Leaflet map.

The address bar follows the screens (`apps/mobile/src/navigation/linking.ts`):
`/markets/<id>`, `/markets/<id>/products/<id>`, `/markets/<id>/cart`, `/orders/<id>`, `/profile`,
`/reset-password?token=…`, `/verify-email?token=…`. Reloads and shared links land on the same
screen. Point `DUKKAN_PASSWORD_RESET_URL` / `DUKKAN_EMAIL_VERIFICATION_URL` of the backend at
`https://<domain>/reset-password` and `/verify-email` so the mails work in any browser.

On wide screens the app stays a centred 520 px column (`index.html`, `FRAME_MAX_WIDTH`).

## Develop

```bash
cd apps/mobile && npm ci          # the app code and its libraries
cd ../customer-web && npm ci
npm run build && npm run preview  # http://localhost:5175, /api proxied to DUKKAN_BACKEND_URL (default :8080)
npm test                          # unit tests of the browser services (vitest, jsdom)
npm run typecheck && npm run lint
npm run smoke                     # Chromium against the running backend (local profile: demo customer)
```

As in `tools/web-preview`, there is no Vite dev server (its pre-bundler cannot read the Flow types
in `react-native`); `npm run dev` builds and serves.

## Deploy

`Dockerfile` (build from the repository root) produces an unprivileged nginx image on port 8080
that serves the files with security headers (CSP, nosniff, Permissions-Policy, no framing) and
`/index.html` for every app route. TLS, HSTS and the `/api` route to the backend belong to the
proxy in front (`infrastructure/production`). Runtime settings, one image for all environments:

| Variable | Default | |
|---|---|---|
| `DUKKAN_API_BASE_URL` | `<origin>/api/v1` | Only if the API is not on the same origin (then also CORS and `DUKKAN_CSP_CONNECT_SRC`) |
| `DUKKAN_MAP_TILE_URL` | OpenStreetMap | Leaflet URL template. Use a tile provider with a contract (MapTiler, Stadia, Thunderforest, own server) for production traffic: the OSM servers' usage policy forbids heavy use |
| `DUKKAN_MAP_ATTRIBUTION` | OpenStreetMap credit | Credit line required by the tile provider |
| `DUKKAN_CSP_CONNECT_SRC` | `'self'` | |

The container runs with a read-only root file system if `/tmp` is writable (tmpfs/emptyDir);
CI checks that.
