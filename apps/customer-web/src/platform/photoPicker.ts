import type { PhotoPicker } from '../../../mobile/src/shared/media/PhotoPicker';
import { chooseFile, encodeJpeg } from './files';

/** As in the app: at most 2048 px on the long side, JPEG quality 0.8. */
const MAX_EDGE = 2048;
const QUALITY = 0.8;

/** The browser's file chooser (or camera on phones); the photo is scaled and re-encoded locally. */
export function createBrowserPhotoPicker(choose: typeof chooseFile = chooseFile): PhotoPicker {
  return {
    async pick(source) {
      const file = await choose('image/jpeg,image/png,image/webp', source === 'camera');
      if (file === null) {
        return { status: 'cancelled' };
      }
      try {
        const bitmap = await createImageBitmap(file);
        const blob = await encodeJpeg(bitmap, bitmap.width, bitmap.height, MAX_EDGE, QUALITY);
        bitmap.close();
        return {
          status: 'picked',
          photo: { uri: URL.createObjectURL(blob), blob, type: 'image/jpeg', fileName: 'photo.jpg', fileSize: blob.size },
        };
      } catch {
        return { status: 'failed', reason: 'other' };
      }
    },
  };
}
