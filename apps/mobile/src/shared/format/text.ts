// Combining diacritics (Latin), Arabic harakat, superscript alef and tatweel.
const MARKS = /[\u0300-\u036F\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640]/g;

/** Lower-cased text without diacritics, for "contains" checks across de/en/ar. */
export function foldForMatch(text: string): string {
  return text.normalize('NFD').replace(MARKS, '').toLowerCase();
}

/**
 * The green organic prefix ("Bio ", "Organic ", "عضوي ") is shown only when the localised name
 * does not already contain the localised word: "Bio-Vollmilch" stays "Bio-Vollmilch", not
 * "Bio Bio-Vollmilch". Case- and diacritic-insensitive.
 */
export function needsOrganicPrefix(name: string, organicWord: string): boolean {
  const word = foldForMatch(organicWord.trim());
  return word !== '' && !foldForMatch(name).includes(word);
}

/** Name with the organic prefix when needed, e.g. for accessibility labels and plain rows. */
export function withOrganicPrefix(name: string, organic: boolean | undefined, organicWord: string): string {
  return organic === true && needsOrganicPrefix(name, organicWord) ? `${organicWord} ${name}` : name;
}
