import { PermissionsAndroid, Platform } from 'react-native';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';
import { DukkanCapture } from 'dukkan-capture';

import {
  SpeechInputError,
  type ShoppingInput,
  type SpeechFailure,
  type VideoPickResult,
} from './ShoppingInput';

/** Frames are scaled to at most this many pixels on the long side: enough to read a label. */
const FRAME_EDGE = 1280;
/** A short clip is enough to pan over a fridge or a list; it stays on the device. */
const VIDEO_SECONDS = 20;

/** The native module is polled for the live transcript (it sends no events). */
const POLL_MS = 250;

const FAILURES: readonly SpeechFailure[] = ['permission', 'unavailable', 'busy', 'network', 'failed'];

function failure(code: unknown): SpeechFailure {
  return FAILURES.find((f) => f === code) ?? 'failed';
}

async function microphoneAllowed(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    // iOS asks inside the native module (microphone and speech recognition prompts).
    return true;
  }
  const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
  return result === PermissionsAndroid.RESULTS.GRANTED;
}

/** The dukkan-capture native module (modules/dukkan-capture) and react-native-image-picker for video. */
export function createNativeShoppingInput(): ShoppingInput {
  return {
    speech: {
      isAvailable: () => DukkanCapture.isSpeechAvailable().catch(() => false),
      async start(languageTag) {
        if (!(await microphoneAllowed())) {
          throw new SpeechInputError('permission');
        }
        try {
          await DukkanCapture.startSpeech(languageTag);
        } catch (error) {
          throw new SpeechInputError(failure((error as { code?: unknown }).code));
        }
      },
      watch(listener) {
        let last = '';
        const timer = setInterval(() => {
          const error = DukkanCapture.speechError();
          const snapshot = {
            listening: DukkanCapture.isListening(),
            transcript: DukkanCapture.speechTranscript(),
            error: error === '' ? null : failure(error),
          };
          const key = `${snapshot.listening}|${snapshot.transcript}|${error}`;
          if (key !== last) {
            last = key;
            listener(snapshot);
          }
        }, POLL_MS);
        return () => clearInterval(timer);
      },
      stop: () => DukkanCapture.stopSpeech(),
      cancel: () => DukkanCapture.cancelSpeech(),
    },
    video: {
      async pick(source, frames): Promise<VideoPickResult> {
        try {
          const response =
            source === 'camera'
              ? await launchCamera({
                  mediaType: 'video',
                  durationLimit: VIDEO_SECONDS,
                  videoQuality: 'medium',
                  saveToPhotos: false,
                  cameraType: 'back',
                })
              : await launchImageLibrary({ mediaType: 'video', selectionLimit: 1 });
          if (response.didCancel === true) {
            return { status: 'cancelled' };
          }
          if (response.errorCode !== undefined) {
            return {
              status: 'failed',
              reason: response.errorCode === 'others' ? 'other' : response.errorCode,
            };
          }
          const uri = response.assets?.[0]?.uri;
          if (uri === undefined) {
            return { status: 'cancelled' };
          }
          const uris = await DukkanCapture.extractVideoFrames(uri, frames, FRAME_EDGE);
          if (uris.length === 0) {
            return { status: 'failed', reason: 'no_frames' };
          }
          return {
            status: 'picked',
            frames: uris.map((frameUri, i) => ({
              uri: frameUri,
              type: 'image/jpeg',
              fileName: `frame-${i + 1}.jpg`,
            })),
          };
        } catch {
          return { status: 'failed', reason: 'no_frames' };
        }
      },
    },
  };
}
