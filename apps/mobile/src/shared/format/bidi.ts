/**
 * Unicode bidi isolation for values inside sentences (right-to-left.md › Numbers).
 *
 * Money, numbers, dates and times always run left to right in de-DE format (decision 3). Inside
 * an Arabic sentence the bidi algorithm would otherwise reorder their neutral characters:
 * "2,90 €" shows as "€ 2,90", "21.10." as ".21.10". Wrapping a value in an isolate keeps it in
 * one piece and in its own direction, and keeps it from reordering the text around it.
 *
 * The marks are invisible but screen readers must not receive them: `forSpeech()` strips them
 * from text that ends up in an accessibility label or hint.
 */
export const LRI = '\u2066';
export const RLI = '\u2067';
export const FSI = '\u2068';
export const PDI = '\u2069';

const BIDI_CONTROLS = /[\u200E\u200F\u202A-\u202E\u2066-\u2069]/g;
const RTL_CHARS = /[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]/;
const DIGIT = /\d/;

/** Left-to-right isolate: for de-DE money, numbers, dates and times, which never contain RTL text. */
export function isolate(text: string): string {
  return text === '' ? text : `${LRI}${text}${PDI}`;
}

/** First-strong isolate: for mixed values whose direction depends on their content. */
export function isolateAuto(text: string): string {
  return text === '' ? text : `${FSI}${text}${PDI}`;
}

/**
 * Isolation for a value interpolated into a translated sentence (the i18n `escape` hook). Only
 * values that contain digits are wrapped (money, counts, dates, times, unit prices, distances):
 * plain words do not get reordered in a way that matters, and leaving them alone keeps names
 * intact. A value with RTL letters (e.g. "6,98 €/كغ") gets a first-strong isolate.
 */
export function isolateValue(value: string): string {
  if (!DIGIT.test(value)) {
    return value;
  }
  return RTL_CHARS.test(value) ? isolateAuto(value) : isolate(value);
}

/** Removes bidi control characters, e.g. before text goes into accessibilityLabel/Hint. */
export function forSpeech(text: string): string;
export function forSpeech(text: string | undefined): string | undefined;
export function forSpeech(text: string | undefined): string | undefined {
  return text === undefined ? undefined : text.replace(BIDI_CONTROLS, '');
}

/** True when the text contains bidi control characters (used by tests). */
export function hasBidiControls(text: string): boolean {
  return new RegExp(BIDI_CONTROLS.source).test(text);
}
