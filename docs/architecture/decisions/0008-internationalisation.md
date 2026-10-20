# ADR 0008: Internationalisation with RTL from day one

- **Status:** Accepted, 2026-10-20

## Context
The app launches in Hamburg with German, English and Arabic, and more languages
will follow. Arabic is written right to left. Product content is entered by
merchants, and customers must find a product whatever language they search in.

## Decision
- **App strings** live in i18next JSON files (`apps/mobile/src/i18n/locales/<lang>.json`). A test enforces that every file has the same keys. The device locale is used, with `de` as fallback, and a globe button switches language at any time.
- **Layout** uses only start/end properties. RTL mirrors layout, chevrons and carousels. Numbers and money always use `de-DE` formatting with Latin digits, which is a product decision.
- **API** clients send `Accept-Language`. The server resolves the language against `supported_locale` with the fallback chain requested → market primary locale → `de` → any, and answers with `Content-Language`. Errors are stable codes that clients translate.
- **Content** is stored in per-entity translation tables (`*_translation`) keyed by locale. Merchants submit `LocalizedText` maps.
- **Search** runs over a normalised `search_text` that combines all translations and keywords (German diacritics folded, Arabic diacritics and letter variants unified), using pg_trgm indexes.
- **Adding a language** means: insert a `supported_locale` row, add one app JSON file, and add the font if a new script needs one. No schema change.

## Consequences
Every new UI string needs three translations at review time. Content missing in
the requested language shows in the market's language. Machine translation
(DeepL or similar) can later fill gaps through the same tables (backlog).
