/** A photo the user picked or took, as a local file the upload can read. */
export interface PickedPhoto {
  uri: string;
  /** MIME type reported by the platform (informational; the server checks the bytes). */
  type: string;
  fileName: string;
  width?: number | undefined;
  height?: number | undefined;
  fileSize?: number | undefined;
}

export type PhotoSource = 'library' | 'camera';

export type PhotoPickFailure = 'permission' | 'camera_unavailable' | 'other';

export type PhotoPickResult =
  | { status: 'picked'; photo: PickedPhoto }
  | { status: 'cancelled' }
  | { status: 'failed'; reason: PhotoPickFailure };

/**
 * Device photo picker behind an interface (like `TokenStorage` and `LocationService`), so screens
 * are tested with a fake and the native library can be swapped.
 */
export interface PhotoPicker {
  pick(source: PhotoSource): Promise<PhotoPickResult>;
}

/** Test/preview picker that answers with the given results in order (the last one repeats). */
export function createFakePhotoPicker(...results: PhotoPickResult[]): PhotoPicker & { calls: PhotoSource[] } {
  const calls: PhotoSource[] = [];
  return {
    calls,
    pick(source) {
      calls.push(source);
      const next = results.length > 1 ? results.shift() : results[0];
      return Promise.resolve(next ?? { status: 'cancelled' });
    },
  };
}
