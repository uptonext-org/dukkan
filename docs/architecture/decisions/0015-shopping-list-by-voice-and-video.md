# ADR 0015: Shopping list by voice, photo or video

- **Status:** Accepted, 2026-10-22

## Context
Customers asked to fill a market's cart by saying or showing what they need ("2 Gurken, 1 kg
Tomaten", a handwritten note, the fridge) instead of searching item by item. The markets' customers
speak German, Arabic and English, often mixed, and many prefer speaking to typing.

## Decision
- **Speech is recognised on the phone**, not on our servers: Android `SpeechRecognizer`, iOS
  `SFSpeechRecognizer`, through the app's own small native module `dukkan-capture`
  (`apps/mobile/modules/dukkan-capture`, a TurboModule). Only the transcript is sent. The common
  third-party module (`@react-native-voice/voice`) still uses `jcenter()` and no Gradle namespace and
  does not build with the app's Gradle 9 / AGP 9. The module also takes still frames from a video
  (`MediaMetadataRetriever` / `AVAssetImageGenerator`), so a video never leaves the device either:
  four JPEG frames of at most 1280 px are sent, like photos.
- **The language model only reads the list** (`shoppinglist` module, `POST
  /markets/{id}/shopping-list`, multipart: `text` and up to four `images`). Claude (same client
  and settings as ADR 0011/0013) returns items with search terms (German and the customer's
  language), an amount and a unit as structured output. Matching stays with the catalog search, so
  the market's assortment is not sent to the model and the answer only contains available products
  of that market. The number of units is computed on the server from the pack size ("1 kg" with a
  500 g pack → 2; "8 Eier" with "10 Stück" → 1), within `maxOrderQuantity`.
- **Optional AI.** Without `DUKKAN_ANTHROPIC_API_KEY` (or when the model fails or declines), a
  rule-based parser reads typed or dictated text (`engine: RULES`; separators, numbers and number
  words, common units in de/en/ar). Photos and videos need the model: the app hides them and says
  so (`GET /shopping-list` → `ai: false`); the server answers 422 `SHOPPING_LIST_AI_UNAVAILABLE`.
- **Nothing is stored or added by the server.** The app shows the matched lines with check boxes,
  alternatives and quantities; the customer confirms, then the app sets the cart quantities (adding
  to what is already in the cart). Requests need a signed-in customer and are limited to 20 per hour
  (`dukkan.shopping-list.requests-per-hour`).

## Consequences
- A second native module to maintain; Android needs `RECORD_AUDIO` (asked when tapping the
  microphone), iOS the microphone and speech recognition usage texts. Apple's and Google's
  recognisers may process audio on their servers as described in the system prompts; the privacy
  notice must say so before launch.
- With a key, transcripts and images of customers go to Anthropic (data-processing agreement as in
  ADR 0011). Transcripts and images are not logged or stored by Dukkan.
- Matching quality is bounded by the catalog search (prefix and substring matches on localised
  names and keywords); the customer always reviews the lines before anything is added.
