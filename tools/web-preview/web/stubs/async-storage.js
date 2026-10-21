// Web stand-in for @react-native-async-storage/async-storage, backed by localStorage.
const PREFIX = 'rn-async:';
const ls = () => window.localStorage;
const AsyncStorage = {
  getItem: async (key) => ls().getItem(PREFIX + key),
  setItem: async (key, value) => ls().setItem(PREFIX + key, value),
  removeItem: async (key) => ls().removeItem(PREFIX + key),
  clear: async () => {
    for (const k of Object.keys(ls())) if (k.startsWith(PREFIX)) ls().removeItem(k);
  },
  getAllKeys: async () => Object.keys(ls()).filter((k) => k.startsWith(PREFIX)).map((k) => k.slice(PREFIX.length)),
};
export default AsyncStorage;
export const { getItem, setItem, removeItem, clear, getAllKeys } = AsyncStorage;
