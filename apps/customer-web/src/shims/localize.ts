// react-native-localize for the browser: the language preferences from navigator.languages.
export function getLocales(languages: readonly string[] = navigator.languages) {
  const tags = languages.length > 0 ? languages : [navigator.language || 'de-DE'];
  return tags.map((tag) => {
    const [languageCode = 'de', countryCode = ''] = tag.split('-');
    return { languageTag: tag, languageCode, countryCode, isRTL: languageCode === 'ar' };
  });
}
export const getTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
export const uses24HourClock = () => true;
export const usesMetricSystem = () => true;
export const getCountry = () => getLocales()[0]?.countryCode ?? 'DE';
export default { getLocales, getTimeZone, uses24HourClock, usesMetricSystem, getCountry };
