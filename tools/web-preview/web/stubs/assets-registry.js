// react-native-svg's web build imports the legacy asset registry, which RN 0.88 no longer ships.
// The app bundles no local image assets, so an empty registry is enough.
const assets = [];
export function registerAsset(asset) {
  return assets.push(asset);
}
export function getAssetByID(id) {
  return assets[id - 1];
}
export default { registerAsset, getAssetByID };
