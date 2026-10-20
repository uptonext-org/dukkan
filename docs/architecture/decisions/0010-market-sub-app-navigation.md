# ADR 0010: Each market is a sub-app with its own tab bar

- **Status:** Accepted, 2026-10-20

## Context
The product owner wants each market to feel like a dedicated shop app (start
page, favourites, search, cart), with nothing mixed between markets.

## Decision
The app has two navigation levels:
1. **Marketplace shell** (tabs Entdecken, Suchen, Warenkörbe, Profil): find markets nearby, search across markets, and see every open cart.
2. **Market sub-app** (tabs Entdecken, Favoriten, Suchen, Warenkorb), opened full-screen on top of the shell and scoped to one `marketId`. It has a close control back to the shell.

Each market has its own cart. Markets can customise only their logo, cover photo
and banners, so every market keeps the same Dukkan design.

## Consequences
Navigation stays shallow and predictable, and cart totals never mix markets. A
customer who buys from several markets places several orders, one per market.
Nested tab bars are unusual on iOS, so the sub-app clearly shows the market's
identity and a close control (HIG: familiarity, agency).
