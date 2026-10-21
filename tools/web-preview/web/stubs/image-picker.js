// Web stand-in for react-native-image-picker: the preview has no photo library, so picking is cancelled.
const cancelled = () => Promise.resolve({ didCancel: true });
export const launchImageLibrary = cancelled;
export const launchCamera = cancelled;
