/**
 * React Native CLI config.
 *
 * `assets` lists the bundled fonts for asset linkers such as `npx react-native-asset`.
 * They are already linked by hand: iOS via the "Fonts" group in Dukkan.xcodeproj plus
 * `UIAppFonts` in Info.plist; Android via `sourceSets.main.assets.srcDirs += ["../../assets"]`
 * in android/app/build.gradle (so they land in the APK as assets/fonts/*.ttf).
 * The font family names equal the PostScript names (e.g. "Inter-SemiBold"), which works on both.
 */
module.exports = {
  assets: ['./assets/fonts/'],
};
