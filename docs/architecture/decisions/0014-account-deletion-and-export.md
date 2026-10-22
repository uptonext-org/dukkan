# ADR 0014: Account deletion and data export

- **Status:** Accepted, 2026-10-22

## Context
The GDPR gives users the right of access and portability (Art. 15, 20) and the right to erasure
(Art. 17). Apple (App Store Review Guideline 5.1.1(v)) and Google Play require that an app with
account creation lets users delete the account from within the app. At the same time, markets must
keep records of their sales (commercial and tax retention), and orders reference the user row.

## Decision
- **Module `privacy`** orchestrates both rights through the other modules' application services, in
  one transaction: `GET /me/export` and `DELETE /me`.
- **Export:** one JSON document (account, linked sign-ins, addresses, orders, favourites,
  notifications and settings, ids of own markets), served as a download and never cached.
- **Deletion:** confirmed with the current password (accounts that only use Apple/Google send none;
  5 attempts per hour). It deletes addresses, carts (releasing slot reservations), favourites,
  devices, inbox and settings, ends every session (refresh tokens, reset links, linked providers),
  and anonymises the account row (placeholder email, unusable password, no roles). The row stays
  because orders refer to it. Orders keep amounts, items, slot, postal code and city for the
  market's records; customer name, street, house number, delivery notes and coordinates are
  removed. The email address is free for a new account afterwards.
- **Blocked cases:** unfinished orders (`ACCOUNT_HAS_OPEN_ORDERS`; cancel or wait) and accounts that
  run a market (`ACCOUNT_OWNS_MARKETS`; support closes or hands over the market, which affects other
  people's orders and the market's own retention).

## Consequences
- An access token issued before the deletion stays valid until it expires (15 minutes) and then
  refers to the anonymised account; the app signs out at once.
- Backups still contain the old data until they rotate; the retention period of backups belongs in
  the privacy notice.
- Merchant offboarding is an operator process for now.
