# ADR 0011: AI product capture with an optional language model

- **Status:** Accepted, 2026-10-21

## Context
Product decision 10 asks for products to be captured by voice, photo or video instead of typing
forms. Merchants of small ethnic groceries often know a product by its Turkish, Arabic or Polish
name and write notes like `Sucuk Gazi 400g 4,49`. Shoppers search in German, English and Arabic
(ADR 0008), so a product needs names in all three languages. The shared catalog (ADR 0009) often
already has the product with every translation, so the best result is a match, not a new product.

## Decision
- **One endpoint, nothing saved.** `POST /merchant/markets/{id}/product-drafts` takes a short text
  and/or the key of an uploaded photo (B-32) and returns a draft: multilingual name and description,
  pack size and net quantity, category, cuisines, organic flag, EU allergen codes, keywords, price,
  barcode, plus up to five shared catalog matches. The merchant reviews it and then creates the
  listing with the existing `createListing` (a catalog match or a new product).
- **Voice is keyboard dictation for now.** iOS and Android keyboards dictate into the text field,
  so no speech-to-text service, audio upload or microphone permission is needed. Video is out of
  scope: one good photo of the pack carries the same information.
- **Provider: Claude API (Messages API, official Java SDK `com.anthropic:anthropic-java`)**, model
  `claude-opus-5-5`, behind the `capture.application.ProductDraftExtractor` port. It reads
  multilingual text and product photos (vision) in one request and returns JSON that follows a
  strict schema (`output_config.format`), so the answer always parses.
  - Effort `low`: reading a label is not hard. Thinking is adaptive and always on for this model.
  - **Server-side fallbacks on by default** (`fallbacks: "default"`, beta header
    `server-side-fallback-2026-07-01`): if the model declines a request, the API retries on the
    model it recommends for that case. A remaining `refusal` is reported as `AI_DECLINED`.
  - 30 s timeout per attempt, one retry; the app waits up to 75 s.
- **The model is optional.** Without `DUKKAN_ANTHROPIC_API_KEY`, and whenever the model fails,
  times out or declines, a deterministic rule-based parser (`capture.domain.RuleBasedDraftParser`)
  reads price, pack size, organic, and category, allergens and cuisine from keywords in German,
  English, Arabic and Turkish (Arabic-Indic digits included). The response says which engine
  answered (`engine`) and why (`warnings`). Development, CI and the demo need no key.
- **Never trust the model's codes.** Category, cuisines, allergens, units and lengths are checked
  against the server's own lists and the contract limits. Unknown values are dropped, so a draft
  can always become a valid `NewCatalogProduct` once the missing fields are filled.
- **The merchant's text is untrusted input.** It is sent as data between `<merchant_input>` tags,
  and the system prompt says to ignore instructions in it. The model has no tools, so injected
  text can at worst produce a wrong draft, which the merchant reviews and the sanitiser bounds.
- **No transaction while waiting.** The model call runs outside a database transaction, and
  drafts are limited to 30 per merchant and hour (they cost money).

## Consequences
- Merchant notes and product photos are sent to Anthropic when a key is configured. They describe
  products, not people, but the privacy notice and the data processing agreement must name
  Anthropic as a processor before the key is set in production. The backend does not log the
  merchant's input.
- Every draft costs one model call (low effort, one image at most 1568 px). The per-merchant limit
  bounds the cost; usage per call is logged (input and output tokens).
- Translations from the model are suggestions; the form shows them for review. Allergens in
  particular are marked as needing a check in the UI.
- Later: barcode lookup against open data (B-34), drafts from several photos, and moderation of
  drafts that become new shared catalog products (B-36).
