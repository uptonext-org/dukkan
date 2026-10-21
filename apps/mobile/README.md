# Dukkan mobile app

React Native 0.88 app (iOS + Android) for the **Dukkan local-market marketplace**: people choose where
they live, see markets nearby as a list or on a map, and open a market as its own "sub-app" (start page,
favourites, search, cart). Merchants maintain their market in the same app ("Mein Markt").

It talks **only** to the API in [`packages/shared-contracts/openapi.yaml`](../../packages/shared-contracts/openapi.yaml)
(v0.2). The server computes every price, total and availability; the app only displays them. Checkout and
payment are simulated. Product decisions: [`docs/product/marketplace.md`](../../docs/product/marketplace.md);
design: [`docs/design/design-system.md`](../../docs/design/design-system.md).

## Setup

```sh
nvm use               # Node 24.22.0 (.nvmrc)
npm ci
# iOS only (macOS + Xcode):
bundle install && (cd ios && bundle exec pod install)
```

Start the backend (`services/backend`, port 8080), then `npm start`, `npm run android` or `npm run ios`.

### API base URL

Resolved in `src/shared/config/env.ts`: Android emulator `http://10.0.2.2:8080/api/v1`, iOS simulator
`http://localhost:8080/api/v1`, or `"apiBaseUrl"` in `app.json` for a device on the LAN.

### Google Maps key (Android)

Android uses Google Maps through react-native-maps and needs a key. It is **never committed**: the manifest
reads the placeholder `${dukkanMapsApiKey}`, which `android/app/build.gradle` fills from the Gradle property
or environment variable `DUKKAN_MAPS_API_KEY`:

```sh
# either
echo "DUKKAN_MAPS_API_KEY=AIza..." >> ~/.gradle/gradle.properties
# or
DUKKAN_MAPS_API_KEY=AIza... npm run android
```

Without a key the app still builds and runs; the Android map just shows an empty grid (list mode, address
search and everything else work). iOS uses Apple Maps and needs no key.

## Native configuration

| Platform | What | Where |
|---|---|---|
| iOS | `NSLocationWhenInUseUsageDescription` (when-in-use only, never "always"; iOS offers "Allow once" / "While using the app") | `ios/Dukkan/Info.plist`, localised in `ios/Dukkan/{de,en,ar}.lproj/InfoPlist.strings` |
| iOS | `CFBundleLocalizations` de, en, ar; `CFBundleDevelopmentRegion` de; project `knownRegions` de/en/ar | `Info.plist`, `project.pbxproj` |
| iOS | Fonts: `UIAppFonts` + a "Fonts" group referencing `../assets/fonts/*.ttf` in the Resources phase | `Info.plist`, `project.pbxproj` |
| iOS | Privacy manifest declares precise location, not linked, not tracking, app functionality | `ios/Dukkan/PrivacyInfo.xcprivacy` |
| Android | `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` (foreground only), `android:supportsRtl="true"` | `AndroidManifest.xml` |
| Android | Maps key meta-data `com.google.android.geo.API_KEY = ${dukkanMapsApiKey}` | `AndroidManifest.xml`, `app/build.gradle` |
| Android | Fonts: `sourceSets.main.assets.srcDirs += ["../../assets"]` → `assets/fonts/*.ttf` in the APK | `app/build.gradle` |
| Both | `react-native.config.js` lists `./assets/fonts/` for asset linkers such as `react-native-asset` (linking above was done by hand) | |

Fonts (OFL 1.1, licence texts next to them): Inter and IBM Plex Sans Arabic, weights 400/500/600/700, in
`assets/fonts`. Files are named after their PostScript names (`Inter-SemiBold.ttf`), which is the
`fontFamily` value on both platforms. The weight lives in the family name; `fontWeight` is not combined with
the custom families (Android would otherwise look for synthetic variants).

## Scripts

| Script | What it does |
|---|---|
| `npm run typecheck` | `tsc --noEmit` (strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`) |
| `npm run lint` | ESLint flat config: typescript-eslint `strictTypeChecked` + react-hooks (React Compiler rules) |
| `npm test` | Jest (RN preset) + React Native Testing Library 14 |
| `npm run audit:prod` | `npm audit --omit=dev` (see VERSIONS.md) |

## Structure

```
src/
  app/            App, AppProviders (Language → Theme → Api → React Query → Auth → Location → AuthGate), AppRoot
  navigation/     Typed param lists, RootNavigator, ShellTabs, shared tab bar, market link helpers
  i18n/locales/   de.json (reference, typed keys), en.json, ar.json
  features/
    onboarding/   "Wo wohnst du?" + map picker with fixed centre pin
    marketplace/  Entdecken: chips, Liste|Karte, filter sheet, MarketCard, MarketMap, ShellHeader
    market/       Market sub-app: MarketNavigator (stack + own tabs), header, home (banners, info card, sections)
    catalog/      Product detail, category/quick-link product lists, ProductGrid/Carousel, useProductActions
    search/       Global search (all nearby markets) and in-market search
    cart/         Warenkörbe tab, market cart (rows, summary, CTA), cart hooks/model
    fulfilment/   Fulfilment sheet (Lieferung|Abholung, address, day chips, slots), link
    favorites/    Favourites screen + own SVG illustration, product/market hearts
    auth/         Session, AuthProvider, AuthForm (customer or merchant), AuthGate (guest → sign-in → continue)
    profile/      Profile, language sheet
    addresses/    Address list/form (address search fills location)
    orders/       Confirmation, list, detail with status timeline, cancel
    merchant/     Mein Markt: markets, profile form + opening hours, listings, add product, promotions, orders
  shared/
    api/          ApiClient (problem+json → ApiError, Accept-Language), DukkanApi (all contract operations), types, query keys
    i18n/         i18next setup, language store (persisted), LanguageProvider (direction, setLanguage)
    theme/        Light/dark tokens (design-system.md §1), typography (Inter / IBM Plex Sans Arabic), makeStyles
    location/     LocationService (geolocation), chosen place (persisted), PlaceSearch (/geo/search)
    storage/      Keychain TokenStorage, AsyncStorage KeyValueStorage
    format/       de-DE money, numbers, dates (Europe/Berlin)
    ui/           AppText, Button/IconButton, Icon, Chip, SegmentedControl, Sheet, ProductTile, stepper, prices, states …
  test/           Fake ApiClient, fixtures, render helpers
```

## Key behaviour

- **i18n**: i18next with bundled resources and **typed keys** (`t('missing.key')` does not compile).
  Start language = persisted choice, else the first supported device language (react-native-localize),
  else `de`. The globe button (shell headers, onboarding) opens Deutsch / English / العربية. The choice is
  stored in AsyncStorage, sent as `Accept-Language` on every request, and saved to the account
  (`PATCH /me preferredLocale`) when signed in. Every query key starts with `[domain, language]`, so
  switching language refetches localised content.
- **Numbers and money** always use `de-DE` with Latin digits (`2,49 €`, `1,2 km`, `08:00–09:00`), also inside
  Arabic sentences. Weekday names come from the locale files so no Arabic-Indic digits appear.
- **Error codes**: every contract error `code` (plus `NETWORK_ERROR`, `TIMEOUT`, `UNKNOWN`) maps to
  `errors.<code>` in all three languages.
- **Arabic copy** is neutral or plural (no gendered singular imperatives); buttons use verbal nouns
  ("اختيار وقت التوصيل"). Six Arabic plural forms are provided where a count is shown.
- **RTL at runtime**: `AppRoot` sets `direction` on the root view. RN 0.88 Fabric resolves `start/end`,
  natural text alignment (`RCTResolveTextAlignment` with the Yoga layout direction) and horizontal
  ScrollView mirroring (`RCTScrollViewComponentView`, Android `SurfaceMountingManager` layoutDirection) from
  the Yoga direction, so the whole JS UI flips instantly, including carousels starting at the start edge.
  All headers are JS (`headerShown: false`), so they flip too. Styles use `start/end`, never `left/right`.
  Chevrons and the back arrow mirror (`Icon` flips `chevron`/`back` in RTL); artwork and logos don't.
  Modals (sheets) set `direction` again because they render in a separate native window.
  What cannot flip at runtime: the native-stack swipe-back gesture direction and system alerts. For those
  `setLanguage` calls `I18nManager.allowRTL/forceRTL`; they follow on the next launch and the language
  sheet tells the user (no programmatic restart: that would need a new native dependency).
- **Theme**: light/dark tokens exactly from design-system.md §1, following `useColorScheme` (no in-app
  appearance switch, per dark-mode.md). Inter for Latin, IBM Plex Sans Arabic when the language is `ar`.
  Type scale per §2; dense rows cap Dynamic Type at 1.6×.
- **Location**: onboarding explains why first, then "Standort verwenden" requests when-in-use permission
  (coarse accuracy is enough) and reverse geocodes via `/geo/reverse`; address search (`/geo/search`,
  300 ms debounce) and "Auf der Karte wählen" (fixed centre pin) are equal alternatives, so a denial keeps
  the screen usable. The place (label + lat/lng) is stored on the device only and changeable from the
  location button in the shell header or the profile.
- **Guest browsing**: everything is browsable. Adding to the cart, hearts, slot choice and checkout open a
  sign-in sheet (`AuthGate`) and continue with the original action after sign-in. A 401 on a public
  endpoint while signed out (backend flag `require-login-to-browse`) opens the same sheet.
- **Server state**: React Query v5 (no 4xx retries). Cart mutations write the returned `Cart` into the cache
  and invalidate `/carts` (tab badge, Warenkörbe). Checkout reuses one `Idempotency-Key` per attempt.
- **Market cart badge**: the market's cart tab shows the cart total ("10,79"), spoken "Warenkorb, 10,79 Euro".
  The shell's Warenkörbe badge shows the number of open carts.
- **Tab bar**: one shared iOS-style tab bar on both platforms (labels under icons, active colour `accent`).
  Tab accessibility labels are localised ("Suchen, Tab 2 von 4"); React Navigation's default appends
  English text.
- **Offline**: NetInfo drives React Query's `onlineManager` and a floating offline banner; error states
  distinguish offline from other errors and offer retry.
- **Accessibility**: roles, labels, states and hints on interactive elements; 44 pt minimum targets; prices
  spoken as "2,49 Euro"; the visible product-tile text block is hidden from screen readers because the
  photo button carries the full summary (avoids reading it twice); progress bars expose values; Reduce
  Motion turns skeleton pulsing off and sheets fade instead of slide.

## Deliberate deviations from the HIG (apple-design skill)

- **Cart badge shows a total**, not a critical count (`tab-bars.md › badges`): decided in design-system.md §5.
- **Onboarding is not a pure pre-alert screen** (`privacy.md › Pre-alert screens`: one button that opens the
  alert). "Wo wohnst du?" is a place chooser: the location button opens the system prompt directly, and
  search/map are alternatives rather than a way to dodge the prompt.
- **One tab-bar design on both platforms** (`cross-platform.md`): iOS style also on Android.
- **Haptics** on add-to-cart are **skipped**: RN core only has `Vibration`, which is not a light impact on
  iOS and would need the VIBRATE permission on Android; a haptics library would be a new native dependency.

## Tests

`npm test` runs 17 suites: i18n key parity (incl. plural categories per language and interpolation
variables), de-DE formatting in Arabic, RTL rendering of ProductTile and MarketCard, onboarding with denied
permission → search, market filters → query, market home sections/banners, guest add-to-cart → sign-in →
continue, 401 on public endpoint, cross-market search, in-market search, cart badge/CTA/fulfilment types and
ordering, Warenkörbe, favourites, profile/registration as merchant, language switch (direction, persistence,
refetch), merchant add-from-catalog, create-product validation, order status actions, and the ApiClient
(`Accept-Language`, problem+json, timeouts). Native modules (maps, geolocation, localize, AsyncStorage,
keychain, netinfo) are mocked in `jest.setup.ts`.

## What was verified here (no Android SDK, Xcode or device)

- `npm run typecheck`, `npm run lint`, `npm test`: pass.
- `react-native bundle --platform android --dev false` and `--platform ios --dev false`: Metro produced both.
- **Not verified**: Gradle/Xcode builds, `pod install`, on-device behaviour (permissions, maps with or without
  a key, fonts actually loading, runtime RTL flip on device), native compatibility of the new native
  libraries with RN 0.88, any call against the real backend.

## Known limitations

- Jest prints "A worker process has failed to exit gracefully" when the full suite runs in parallel. All tests
  pass; `--detectOpenHandles` reports no handle and React Query clients are disposed after each test.
- `CartItem` has no `maxOrderQuantity`, so the cart-row stepper caps at the contract's hard cap 99 and the
  server enforces the listing limit (`MAX_QUANTITY_EXCEEDED`). Tiles and product detail use the real limit.
- New merchant products need a `categoryId`, but the contract has no category list besides
  `/markets/{id}/categories` (only categories that already have products in the market). A brand-new market
  must first add one catalog product; the form says so.
- Editing a market profile prefills the description only in the current UI language, because the API
  returns market content localised; other languages start empty and are omitted if left empty.
- The opening-hours editor supports one interval per day (the contract allows several).
- Images are URLs (phase 1); there is no upload.
- API types are hand-written (`src/shared/api/types.ts`); openapi-typescript does not support TS 6.
