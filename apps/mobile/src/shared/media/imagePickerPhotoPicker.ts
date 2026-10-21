import { launchCamera, launchImageLibrary, type Asset, type ImagePickerResponse } from 'react-native-image-picker';

import type { PhotoPicker, PhotoPickResult } from './PhotoPicker';

/**
 * Photos are downscaled and re-encoded on the device before upload: at most 2048 px on the long
 * side, JPEG quality 0.8 (HEIC from iPhones becomes JPEG). That keeps uploads well below the
 * server's 5 MB / 40 MP limits and strips most metadata.
 */
const PHOTO_OPTIONS = {
  mediaType: 'photo',
  maxWidth: 2048,
  maxHeight: 2048,
  quality: 0.8,
  includeBase64: false,
  includeExtra: false,
} as const;

function toResult(response: ImagePickerResponse): PhotoPickResult {
  if (response.didCancel === true) {
    return { status: 'cancelled' };
  }
  if (response.errorCode !== undefined) {
    return { status: 'failed', reason: response.errorCode === 'others' ? 'other' : response.errorCode };
  }
  const asset: Asset | undefined = response.assets?.[0];
  if (asset?.uri === undefined) {
    return { status: 'cancelled' };
  }
  const type = asset.type ?? 'image/jpeg';
  return {
    status: 'picked',
    photo: {
      uri: asset.uri,
      type,
      fileName: asset.fileName ?? `photo.${type === 'image/png' ? 'png' : type === 'image/webp' ? 'webp' : 'jpg'}`,
      width: asset.width,
      height: asset.height,
      fileSize: asset.fileSize,
    },
  };
}

/**
 * react-native-image-picker: the system photo picker (iOS PHPicker, Android Photo Picker; no
 * storage permission needed) and the system camera app (iOS asks for camera access with
 * NSCameraUsageDescription; Android uses an ACTION_IMAGE_CAPTURE intent, no CAMERA permission).
 */
export function createImagePickerPhotoPicker(): PhotoPicker {
  return {
    async pick(source) {
      try {
        const response =
          source === 'camera'
            ? await launchCamera({ ...PHOTO_OPTIONS, saveToPhotos: false, cameraType: 'back' })
            : await launchImageLibrary({ ...PHOTO_OPTIONS, selectionLimit: 1, restrictMimeTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/heic'] });
        return toResult(response);
      } catch {
        return { status: 'failed', reason: 'other' };
      }
    },
  };
}
