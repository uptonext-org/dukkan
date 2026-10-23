import type { VideoFramePicker } from '../../../mobile/src/shared/shoppingInput/ShoppingInput';
import { chooseFile, encodeJpeg } from './files';

/** As in the app: frames at most 1280 px on the long side; the video itself never leaves the browser. */
const FRAME_EDGE = 1280;
const QUALITY = 0.8;

function once(target: HTMLVideoElement, event: string): Promise<void> {
  return new Promise((resolve, reject) => {
    target.addEventListener(event, () => resolve(), { once: true });
    target.addEventListener('error', () => reject(new Error('video')), { once: true });
  });
}

/** Evenly spread moments, away from the very start and end (often black or blurred). */
export function frameTimes(duration: number, count: number): number[] {
  if (!Number.isFinite(duration) || duration <= 0 || count <= 0) {
    return [];
  }
  return Array.from({ length: count }, (_, i) => (duration * (i + 0.5)) / count);
}

async function extractFrames(file: File, count: number): Promise<Blob[]> {
  const url = URL.createObjectURL(file);
  const video = document.createElement('video');
  video.muted = true;
  video.playsInline = true;
  video.preload = 'auto';
  video.src = url;
  try {
    await once(video, 'loadeddata');
    const frames: Blob[] = [];
    for (const time of frameTimes(video.duration, count)) {
      const seeked = once(video, 'seeked');
      video.currentTime = time;
      await seeked;
      frames.push(await encodeJpeg(video, video.videoWidth, video.videoHeight, FRAME_EDGE, QUALITY));
    }
    return frames;
  } finally {
    video.removeAttribute('src');
    video.load();
    URL.revokeObjectURL(url);
  }
}

/** Records (phones) or picks a video and returns still frames of it, like the app's native module. */
export function createBrowserVideoFramePicker(choose: typeof chooseFile = chooseFile): VideoFramePicker {
  return {
    async pick(source, frames) {
      const file = await choose('video/*', source === 'camera');
      if (file === null) {
        return { status: 'cancelled' };
      }
      try {
        const blobs = await extractFrames(file, frames);
        if (blobs.length === 0) {
          return { status: 'failed', reason: 'no_frames' };
        }
        return {
          status: 'picked',
          frames: blobs.map((blob, i) => ({
            uri: URL.createObjectURL(blob),
            blob,
            type: 'image/jpeg',
            fileName: `frame-${i + 1}.jpg`,
          })),
        };
      } catch {
        return { status: 'failed', reason: 'no_frames' };
      }
    },
  };
}
