# Dukkan web portal

Browser portal next to the app (backlog B-31, B-36). The mobile merchant mode stays for quick
actions; the portal is for work at a desk:

- **Bestellungen**: orders board in four columns (new, accepted, ready/on the way, done), the next
  allowed status as buttons, refreshes every 30 seconds.
- **Sortiment**: all listings with search; change price and availability inline.
- **CSV-Import**: upload a sheet (Excel "CSV UTF-8", `;` or `,`), check every row first (dry run),
  then import all at once. Template download included. Endpoint and columns: `importListings` in
  `packages/shared-contracts/openapi.yaml`.
- **Marktprüfung** (role ADMIN): markets by status; approve, suspend, return to review.

German, English and Arabic (right-to-left), light and dark theme (system setting).

```bash
npm ci
npm run dev        # http://localhost:5174, /api proxied to http://localhost:8080 (DUKKAN_BACKEND_URL)
npm test           # vitest + Testing Library against a fake backend
npm run build      # static files in dist/
```

Demo accounts (local profile): `haendler@dukkan.local`, `admin@dukkan.local`, password in
`docs/setup.md`. Customer accounts are turned away.

**Deployment:** `dist/` is static. Serve it on the same origin as the API (the ingress routes
`/api` to the backend and everything else to the files), so no CORS setup is needed. Sessions live
in `sessionStorage` (closed with the tab); access tokens renew silently with the refresh token.
