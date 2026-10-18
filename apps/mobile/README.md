# Dukkan mobile app

React Native 0.88 app (iOS + Android) for the Dukkan grocery-delivery MVP. It talks **only** to the Dukkan
backend described in [`packages/shared-contracts/openapi.yaml`](../../packages/shared-contracts/openapi.yaml).
The server computes every price and total; the app only displays them. Checkout is simulated and nothing is charged.

## Setup

```sh
nvm use               # Node 24.22.0 (.nvmrc)
npm ci
# iOS only (macOS + Xcode):
bundle install && (cd ios && bundle exec pod install)
```

Start the backend (`services/backend`, port 8080), then:

```sh
npm start             # Metro
npm run android       # needs Android SDK + emulator/device
npm run ios           # needs Xcode + simulator
```

### API base URL

The app resolves the URL in `src/shared/config/env.ts`:

| Situation | URL |
|---|---|
| Android emulator (default) | `http://10.0.2.2:8080/api/v1` |
| iOS simulator (default) | `http://localhost:8080/api/v1` |
| Physical device or other host | Set `"apiBaseUrl"` in `app.json`, e.g. `"http://192.168.1.20:8080/api/v1"` |

Cleartext HTTP to local hosts works out of the box. Android debug builds allow cleartext through the template's
`usesCleartextTraffic` placeholder, and iOS allows `NSAllowsLocalNetworking`. Release builds need HTTPS.

## Scripts

| Script | What it does |
|---|---|
| `npm start` / `android` / `ios` | Metro / build and run (React Native CLI) |
| `npm run typecheck` | `tsc --noEmit` (strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`) |
| `npm run lint` | ESLint flat config: typescript-eslint `strictTypeChecked` + react-hooks |
| `npm test` | Jest (RN preset) + React Native Testing Library 14 |
| `npm run test:ci` | `jest --ci --coverage` |
| `npm run format` / `format:check` | Prettier (2.8.8, template version) |
| `npm run audit:prod` | `npm audit --omit=dev`; see VERSIONS.md for the current result |

## Structure

```
src/
  app/            App root, providers (Api, React Query, Auth, SafeArea), dependency wiring
  navigation/     Typed RootStackParamList / TabParamList, root stack + bottom tabs, useRootNavigation()
  features/
    auth/         AuthProvider, session (token in memory + TokenStorage), Login/Register, validation
    catalog/      Home (categories tree + offers carousel), CategoryProducts (infinite scroll), ProductGrid
    search/       Debounced search, suggestions, sort, "only offers"
    product/      ProductDetail (stepper bounded by maxOrderQuantity)
    cart/         Cart (lines, totals, minimum-order progress, slot, simulated checkout), cart hooks
    promotions/   Active promotions
    address/      AddressList / AddressForm (postal code ^[0-9]{5}$, serviceability pre-check on blur)
    delivery/     DeliverySlotPicker (grouped by day in Europe/Berlin, unavailable slots disabled)
    orders/       OrderConfirmation, OrderList, OrderDetail (+ cancel)
    profile/      Profile, sign out
  shared/
    api/          ApiClient interface + fetch implementation, ApiError, typed DukkanApi, query keys/client
    ui/           Button, TextField, Price, QuantityStepper, ProductTile, Screen, states, Badge, ...
    theme/ config/ storage/ network/ format/ hooks/
  test/           Fake ApiClient, fixtures, render helpers
```

Each feature has `screens/`, `components/` and `api/` (React Query hooks) where it needs them, plus `model.ts` /
`validation.ts` for pure logic.

### Key behaviour

- **HTTP**: `createFetchApiClient` builds URLs, sends JSON and injects `Authorization: Bearer`. It maps
  `application/problem+json` to `ApiError { kind, status, code, detail, fieldErrors }`, times out through
  `AbortController` (15 s), and maps network failures to `kind: 'network'`.
- **401**: an authenticated 401 calls `session.expire()`. That clears the token and the query cache, and the root
  navigator switches to Login.
- **Server state**: React Query v5. Queries retry transient errors twice and never retry 4xx. Mutations don't retry.
  Cart mutations write the returned `Cart` into the cache, because the server is the source of truth. Checkout reuses
  one `Idempotency-Key` per attempt.
- **Offline**: NetInfo drives React Query's `onlineManager` and a global offline banner. Error states tell offline
  apart from other failures and offer retry.
- **Token storage**: `react-native-keychain` sits behind the `TokenStorage` interface. Tests use the in-memory
  implementation.
- **Branding**: an original palette (deep green `#1F6F50` on warm off-white `#FAF7F2`). There are no images or icon
  fonts. Products show an initials tile because `imageUrl` is null in the seed data. Launcher icons are
  original generated PNGs.
- **Accessibility**: roles, labels and hints on every interactive element. Prices are spoken as "2,49 Euro". Touch
  targets are at least 44 pt, text respects system font scaling, and text/background pairs meet WCAG AA contrast.

## What was verified in the build sandbox

There was no Android SDK, Xcode or device:

- `npm run typecheck`, `npm run lint`, `npm test`, `npm run test:ci`: all pass.
- `react-native bundle` for android and ios (`--dev false`): Metro produced both bundles.
- **Not verified**: Gradle/Xcode builds, CocoaPods install, on-device runs, and native compatibility of
  `react-native-keychain@10.0.0` and `react-native-screens@4.30.0` with RN 0.88. No calls were made to a running
  backend.

## Known limitations

- API types are written by hand (`src/shared/api/types.ts`), because openapi-typescript 7.x does not support
  TypeScript 6. Keep them in sync with the contract.
- The app has a light theme only, and its strings are English and inline (no i18n framework yet).
- Every screen requires sign-in. There is no guest browsing, even though catalog endpoints are public.
- Product tiles cap the stepper at the contract default of 24, because `ProductSummary` has no `maxOrderQuantity`.
  The server enforces the real limit (`MAX_QUANTITY_EXCEEDED`). Product detail uses the real value.
- The "add X more" minimum-order hint is computed for display only, as `minimumOrderValue - (subtotal - discountTotal)`.
  Checkout eligibility always comes from `meetsMinimumOrderValue`.
