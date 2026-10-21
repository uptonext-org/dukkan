// Web stand-in for react-native-localize, from navigator.languages.
export function getLocales() {
  const tags = navigator.languages?.length ? navigator.languages : [navigator.language || 'de-DE'];
  return tags.map((tag) => {
    const [languageCode, countryCode = ''] = tag.split('-');
    return { languageTag: tag, languageCode, countryCode, isRTL: languageCode === 'ar' };
  });
}
export const getTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
export const uses24HourClock = () => true;
export const usesMetricSystem = () => true;
export const getCountry = () => getLocales()[0].countryCode || 'DE';
export const findBestLanguageTag = (tags) => {
  const locales = getLocales();
  for (const l of locales) {
    if (tags.includes(l.languageTag)) return { languageTag: l.languageTag, isRTL: l.isRTL };
    if (tags.includes(l.languageCode)) return { languageTag: l.languageCode, isRTL: l.isRTL };
  }
  return undefined;
};
export default { getLocales, getTimeZone, uses24HourClock, usesMetricSystem, getCountry, findBestLanguageTag };
