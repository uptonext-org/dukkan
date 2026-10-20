# Dukkan design system

The look follows the mood of the reference screenshots the product owner
supplied (white surfaces, fresh green, red accent, product photos on soft
tinted tiles, large bold titles). It is grounded in Apple's Human Interface
Guidelines, used as a reference through the `apple-design` skill. The hues,
illustrations, copy and name are **our own**: we use no third-party marks,
photos or mascots. The reference screenshots stay outside the repository.

## 1. Tokens

Every component reads tokens from the theme and never uses literal colours.
The light and dark themes share the same token names. All text/background
pairs below were checked against WCAG AA (≥ 4.5:1 for body text).

| Token | Light | Dark | Use | Contrast (light / dark) |
|---|---|---|---|---|
| `bg` | `#FFFFFF` | `#121212` | Screen background | |
| `surface` | `#FFFFFF` | `#1E1E1E` | Cards, sheets | |
| `surfaceMuted` | `#F3F1EC` | `#262523` | Search field, quantity pill, delivery-time bar | |
| `surfaceRose` | `#FCF1EE` | `#2A1F1D` | Welcome/info card | |
| `tileSage` | `#EEF0E3` | `#23261E` | Product photo tile | |
| `tileSky` / `tileBlush` / `tileSand` | `#E6EEF3` / `#F6E7E9` / `#F4EFE4` | `#1E2428` / `#2A2022` / `#28251F` | Alternating tile tints, category icon tiles | |
| `text` | `#2B2B2B` | `#F2F0EC` | Primary text | 14.2 / 16.5 |
| `textSecondary` | `#5C5752` | `#B3ADA6` | Subtitles, unit prices | 7.1 / 7.5 |
| `textPlaceholder` | `#6E6862` | `#9C968F` | Placeholder on `surfaceMuted` | 4.9 |
| `primary` | `#3F7A24` | `#7CC25A` | Links ("Lieferzeit wählen ›"), the "Bio" prefix | 5.2 / 8.7 |
| `primaryStrong` | `#2E5320` | `#3F7A24` | Discount label in cart, badges on dark green | white text 8.8 |
| `accent` | `#C42F28` | `#FF6B5E` | Active tab, sale price, cart badge, main CTA | 5.6 / 6.7 |
| `onAccent` | `#FFFFFF` | `#FFFFFF` | Text on `accent` (dark CTA uses `#D2392F` = 4.8) | 5.6 |
| `promo` | `#F5DA4B` | `#E8C93A` | "20 % Rabatt" pill on product tiles (text `#2B2B2B`) | 10.1 |
| `taste` | `#7A6418` | `#D4B455` | Gold tagline above product names ("süß-säuerlich") | 5.7 |
| `separator` | `#ECE9E5` | `#2E2C2A` | Hairlines between rows | |
| `overlayText` | `#FFFFFF` + 35 % black scrim | same | Text on banner photos | |

**Radii:** 6 (labels), 12 (pills, fields), 16 (tiles, banners), 24 (sheets), full (badge, circle buttons).
**Spacing scale:** 4, 8, 12, 16, 20, 24, 32. Screen gutter 16; the hero carousel lets the next card peek about 24 pt.
**Elevation:** flat. Cards are separated by tint, not shadow; the floating "+" button gets a soft shadow (y 1, blur 4, 12 %).
**Touch targets:** at least 44 × 44 pt (HIG accessibility).

## 2. Typography

- **Latin:** Inter (OFL). **Arabic:** IBM Plex Sans Arabic (OFL), selected automatically when the locale is `ar`.
- Every style scales with the system text size (`allowFontScaling`), with a maximum multiplier of 1.6 on dense rows.

| Style | Size / line height | Weight | Example |
|---|---|---|---|
| `largeTitle` | 30 / 36 | 700 | "Hier kommen deine Favoriten" |
| `title` | 24 / 30 | 700 | Section headings, cart total |
| `bannerTitle` | 28 / 34 | 700 | Text on banner photos |
| `headline` | 18 / 24 | 600 | Product name on tile, list rows |
| `body` | 17 / 24 | 400 | Body copy, list rows |
| `callout` | 16 / 22 | 400 | Tab-bar-adjacent copy |
| `subhead` | 15 / 20 | 400 | Secondary lines, unit prices |
| `caption` | 13 / 18 | 500 | Badges, tab labels (11 / 13 on the tab bar) |
| `price` | 18 / 22 | 600 | Price; on cart rows the cents are superscript (`2¹⁴`) |

## 3. Internationalisation and RTL

- The locales are `de` (fallback), `en` and `ar`. The app starts in the device locale and offers a globe button at the top end (top right in LTR) to switch language.
- Layout uses `start`/`end` (`paddingStart`, `marginEnd`, `textAlign: 'auto'`) and never `left`/`right`. Chevrons and the back arrow mirror in RTL, and carousels scroll from the start edge.
- **Numbers and money always use `de-DE` formatting with Latin digits, in every language** (decision 3): `2,49 €`, `1,2 km`, `08:00–09:00`.
- Copy is neutral in Arabic (no gendered imperatives).
- Every user-facing string lives in `src/i18n/locales/<lang>.json`. Content from the backend arrives already localised (`Accept-Language`).

## 4. Screens

### 4.1 Onboarding: "Wo wohnst du?"
- A large title and a one-line reason ("Damit wir dir Märkte in deiner Nähe zeigen können"), per HIG privacy: explain before the system prompt.
- Primary button **"Standort verwenden"** opens the system prompt (one-time or while in use; never "always").
- A search field with live address suggestions (`/geo/search`).
- A secondary **"Auf der Karte wählen"** opens a map with a fixed centre pin and a confirm button.
- If permission is denied, the screen stays usable through search or the map. The chosen location is stored locally and can be changed from the header.

### 4.2 Marketplace shell: bottom tabs **Entdecken · Suchen · Warenkörbe · Profil**
- **Entdecken (markets near me):**
  - Header: location button ("📍 St. Georg ›" style, with a pin icon, not an emoji), globe language button, account button.
  - A row of horizontally scrolling type chips (Alle, Supermarkt, Bäckerei, Metzgerei, …) and a cuisine chip row.
  - A **Liste | Karte** segmented control, and a sort/filter button that opens a sheet: sort by distance, delivery fee or minimum order; filters for delivery, pickup, open now and has offers.
  - Market card: 16:9 cover photo with a logo circle overlapping the bottom-start corner; name; type · cuisine; distance · open/closed ("Geöffnet bis 20:00"); chips for delivery and pickup; "Mindestbestellwert 15 €" and "Lieferung 2,99 €"; a red "3 Angebote" badge.
  - Map mode: one pin per market (logo or initial), and a bottom card for the selected market.
- **Suchen (all markets):** search field, then results grouped by product, each with the nearby markets that sell it and their prices. Tapping one opens that product in the market's sub-app.
- **Warenkörbe:** one row per non-empty cart (market logo, name, item count, total). Tapping it opens the market's cart. The tab badge shows the number of open carts.
- **Profil:** account (or sign-in), language, addresses, orders, and "Mein Markt" for merchants.

### 4.3 Market sub-app: its own bottom tabs **Entdecken · Favoriten · Suchen · Warenkorb**
A close/back control at the top start returns to the marketplace. The market's name and logo appear in the header.
- **Entdecken:** green "Lieferzeit wählen ›" or "Abholzeit wählen ›" link on the start side of the header and the account icon on the end side. Then a hero carousel of banners (cover photo, white overline and title on a scrim, rounded 16 corners, next card peeks), the market info card on `surfaceRose` (opening hours, delivery/pickup, minimum order), and horizontal sections ("Angebote der Woche", "Neu im Markt", top categories) of product tiles.
- **Product tile (2-column grid or carousel item):**
  - Photo on `tileSage` with radius 16. A yellow promo pill at the bottom start, and a white square "+" button at the bottom end (it becomes a − n + stepper once added).
  - Below the photo: an optional gold tagline, then the name in `headline` with a green "Bio " prefix when organic and a trailing ›.
  - Price in `accent` with the old price struck through in `textSecondary` when discounted, otherwise in `text`. Last line: "550 g · 5,07 €/kg".
- **Favoriten:** large title "Hier kommen deine Favoriten", an explanation, and an illustration of three tilted tiles with heart badges (our own vector drawing). Then product tiles grouped by category headers ("Obst"). Heart toggles appear on the tile and on product detail.
- **Suchen:** a rounded `surfaceMuted` search field ("Suche nach Produkten"); quick links with 72 pt tinted icon tiles ("Alle Angebote" yellow %, "Neu im Markt" pink sparkles, "Regional"); a separator; "Alle Kategorien" as rows with a square image tile and a chevron. Typing shows suggestions, then a grid of results with sort and filter.
- **Warenkorb:**
  - A `surfaceMuted` bar at the top: truck icon, "Lieferzeit wählen" (or the chosen slot), and a ⋯ menu (empty the cart).
  - Each row: a quantity pill on the start side, a 64 pt image, the name, the unit size, a `primaryStrong` discount label, and on the end side the old price struck through with the new price in `accent`, cents in superscript.
  - Summary: Rabatt, Lieferung ("Gratis" or the amount), **Total (inkl. MwSt.)**, and a minimum-order progress bar.
  - A sticky full-width `accent` button: "Lieferzeit wählen" until a slot is reserved, then "Bestellen · 23,40 €".
- **Fulfilment sheet:** segmented Lieferung | Abholung (only the options the market offers), days as chips, and time windows as rows with fee and remaining capacity. Unavailable windows are disabled and announced as such.
- **Order confirmation:** a check illustration, the order number, the slot, and a status timeline (simulated).

### 4.4 Merchant mode ("Mein Markt")
Profile form (photos as URLs in phase 1), product list with an availability toggle and price, "Produkt hinzufügen" (search the shared catalog with a cuisine filter, or create a new product), offers, and incoming orders with status buttons.

## 5. Platform conventions (React Native, iOS and Android)
- The tab bar follows the iOS style on both platforms (one shared design): labels under icons, active colour `accent`.
- The cart badge shows the cart total ("10,79"). This is a deliberate deviation from `tab-bars.md › badges` (badges are meant for critical counts), and the accessibility label reads "Warenkorb, 10,79 Euro".
- Back navigation: the native stack back on iOS, plus the hardware back button on Android.
- Haptic light impact when adding to the cart (`motion.md`/`playing-haptics.md`); respect Reduce Motion.
- Every interactive element has a role, a label and a state, and the screen-reader order follows the visual order in both LTR and RTL.
