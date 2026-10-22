import type { PhotoPickFailure, PhotoSource, PickedPhoto } from '../media';

export type SpeechFailure = 'permission' | 'unavailable' | 'busy' | 'network' | 'failed';

export class SpeechInputError extends Error {
  constructor(readonly reason: SpeechFailure) {
    super(`speech: ${reason}`);
    this.name = 'SpeechInputError';
  }
}

export interface SpeechSnapshot {
  listening: boolean;
  /** Everything recognised so far. */
  transcript: string;
  error: SpeechFailure | null;
}

/**
 * Speech to text on the device (Android SpeechRecognizer, iOS SFSpeechRecognizer); only the text is
 * sent to Dukkan. Listening runs until `stop`; `watch` reports the live transcript and when the
 * recogniser stops by itself (an error or its time limit).
 */
export interface SpeechInput {
  isAvailable(): Promise<boolean>;
  /** Asks for the microphone if needed; throws a SpeechInputError. */
  start(languageTag: string): Promise<void>;
  /** Calls `listener` with each change while listening; returns the unsubscribe function. */
  watch(listener: (snapshot: SpeechSnapshot) => void): () => void;
  /** Stops listening and resolves with the final transcript. */
  stop(): Promise<string>;
  cancel(): void;
}

export type VideoPickResult =
  | { status: 'picked'; frames: PickedPhoto[] }
  | { status: 'cancelled' }
  | { status: 'failed'; reason: PhotoPickFailure | 'no_frames' };

/** Records or picks a short video and returns a few still frames of it (the video itself is not uploaded). */
export interface VideoFramePicker {
  pick(source: PhotoSource, frames: number): Promise<VideoPickResult>;
}

export interface ShoppingInput {
  speech: SpeechInput;
  video: VideoFramePicker;
}

/** Device features that do nothing: for platforms or tests without them. */
export function createUnavailableShoppingInput(): ShoppingInput {
  return {
    speech: {
      isAvailable: () => Promise.resolve(false),
      start: () => Promise.reject(new SpeechInputError('unavailable')),
      watch: () => () => undefined,
      stop: () => Promise.resolve(''),
      cancel: () => undefined,
    },
    video: { pick: () => Promise.resolve({ status: 'cancelled' }) },
  };
}

/**
 * Test input: `start` begins "listening" and `say` adds words to the live transcript, as the
 * recogniser would; `stop` returns it. Video picks answer with the given results in order.
 */
export function createFakeShoppingInput(
  options: { available?: boolean; startError?: SpeechFailure; videos?: VideoPickResult[] } = {},
) {
  let listening = false;
  let transcript = '';
  const listeners = new Set<(snapshot: SpeechSnapshot) => void>();
  const notify = () => listeners.forEach((listener) => listener({ listening, transcript, error: null }));
  const videos = [...(options.videos ?? [{ status: 'cancelled' } as const])];
  const calls = { languages: [] as string[], videoSources: [] as PhotoSource[] };
  const input: ShoppingInput = {
    speech: {
      isAvailable: () => Promise.resolve(options.available ?? true),
      start(languageTag) {
        calls.languages.push(languageTag);
        if (options.startError !== undefined) {
          return Promise.reject(new SpeechInputError(options.startError));
        }
        listening = true;
        transcript = '';
        return Promise.resolve();
      },
      watch(listener) {
        listeners.add(listener);
        return () => listeners.delete(listener);
      },
      stop() {
        listening = false;
        return Promise.resolve(transcript);
      },
      cancel() {
        listening = false;
      },
    },
    video: {
      pick(source) {
        calls.videoSources.push(source);
        const next = videos.length > 1 ? videos.shift() : videos[0];
        return Promise.resolve(next ?? { status: 'cancelled' });
      },
    },
  };
  return {
    input,
    calls,
    say(words: string) {
      transcript = transcript === '' ? words : `${transcript} ${words}`;
      notify();
    },
    /** The recogniser stops by itself (silence limit), as on the device. */
    finish() {
      listening = false;
      notify();
    },
  };
}
