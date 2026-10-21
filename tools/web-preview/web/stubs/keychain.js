// Web stand-in for react-native-keychain, backed by localStorage. Preview only: not secure.
const key = (service) => `rn-keychain:${service ?? 'default'}`;
export const ACCESSIBLE = {
  WHEN_UNLOCKED: 'WHEN_UNLOCKED',
  AFTER_FIRST_UNLOCK: 'AFTER_FIRST_UNLOCK',
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'WHEN_UNLOCKED_THIS_DEVICE_ONLY',
};
export async function getGenericPassword(options = {}) {
  const raw = window.localStorage.getItem(key(options.service));
  if (raw === null) return false;
  const { username, password } = JSON.parse(raw);
  return { username, password, service: options.service ?? 'default', storage: 'localStorage' };
}
export async function setGenericPassword(username, password, options = {}) {
  window.localStorage.setItem(key(options.service), JSON.stringify({ username, password }));
  return { service: options.service ?? 'default', storage: 'localStorage' };
}
export async function resetGenericPassword(options = {}) {
  window.localStorage.removeItem(key(options.service));
  return true;
}
export default { ACCESSIBLE, getGenericPassword, setGenericPassword, resetGenericPassword };
