# ADR 0013: Machine translation of merchant texts

- **Status:** Accepted, 2026-10-21

## Context
Shoppers search and read in German, English and Arabic (ADR 0008). Shared catalog products carry all
three languages, but merchants write their private products, promotions and market descriptions in
one language, usually German or Turkish-flavoured German. Such a product is then invisible to an
Arabic or English search, and the backlog item B-38 asked for missing locales to be filled.

## Decision
- **Provider: the same Claude API client as AI capture** (`dukkan.ai.anthropic`, ADR 0011), shared
  through `shared.ai.AnthropicClients`, behind the `translation.application.Translator` port. One
  key and one data-processing agreement instead of a second vendor (DeepL). Claude handles the
  shop vocabulary (brands, units, dishes like *Simit* or *Labneh*) well, and the answer is
  structured output with a schema built for exactly the requested keys and locales, so every text
  comes back or the call counts as failed. Effort `low`, server-side fallbacks as in ADR 0011.
- **Two uses:**
  1. *Suggestions in the form* — `POST /merchant/translations` with a `kind` (product name,
     description, promotion, market description) and fields of locale → text. The server translates
     every enabled locale that is empty, returns only those, and saves nothing; the app's
     "Fehlende Sprachen übersetzen" button fills the empty fields and asks the merchant to review.
  2. *Background completion* — creating a private product appends the outbox event
     `catalog.private_product_created` (ADR 0012). The handler `private-product-translation`
     translates name and description into the missing locales, stores them with
     `machine_translated = true` (migration V9) and rebuilds the search text, so the product is
     found in every language. A merchant's own text is never overwritten (`ON CONFLICT DO NOTHING`).
- **Optional, like capture.** Without a key the endpoint answers 422 `TRANSLATION_UNAVAILABLE` and the
  handler does nothing; products stay in the merchant's languages and the existing locale fallback
  applies. A failed or declined call is handled the same way; the event is not retried, since the
  product works without the translation.
- **Untrusted input.** Merchant texts go in as JSON data inside `<texts>`; the system prompt says they
  are never instructions. Limits: 20 fields, 2000 characters per text and 8000 per request, 60
  requests per merchant and hour (`dukkan.translation.requests-per-hour`). Texts are not logged.

## Consequences
- With a key, merchant texts are sent to Anthropic; this must be in the privacy notice and the DPA
  (as for ADR 0011).
- Machine translations are marked in the database; a later "review translations" view for merchants
  can use the flag. Editing a product does not re-translate yet.
- A new language (e.g. Turkish) only needs a row in `supported_locale` and a name in the prompt's
  language list; both the endpoint and the handler use the enabled locales.
