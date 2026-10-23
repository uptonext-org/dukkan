import {
  SpeechInputError,
  type SpeechFailure,
  type SpeechInput,
  type SpeechSnapshot,
} from '../../../mobile/src/shared/shoppingInput/ShoppingInput';

/** The parts of the Web Speech API used here (not in TypeScript's DOM library). */
interface RecognitionResultList {
  readonly length: number;
  [index: number]: { readonly 0: { readonly transcript: string } };
}
export interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: { results: RecognitionResultList }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}
export type RecognitionConstructor = new () => Recognition;

export function findRecognition(scope: object = window): RecognitionConstructor | undefined {
  const s = scope as { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor };
  return s.SpeechRecognition ?? s.webkitSpeechRecognition;
}

function failure(code: string): SpeechFailure {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'permission';
    case 'network':
      return 'network';
    case 'audio-capture':
    case 'language-not-supported':
      return 'unavailable';
    default:
      return 'failed';
  }
}

/**
 * Dictation with the browser's recogniser. Unlike the app, Chrome and Safari may process the audio
 * on Google's or Apple's servers; the privacy notice says so. Only the text reaches Dukkan.
 */
export function createBrowserSpeechInput(Ctor: RecognitionConstructor | undefined = findRecognition()): SpeechInput {
  let recognition: Recognition | null = null;
  let snapshot: SpeechSnapshot = { listening: false, transcript: '', error: null };
  const listeners = new Set<(s: SpeechSnapshot) => void>();
  let ended: (() => void) | null = null;
  const update = (next: Partial<SpeechSnapshot>) => {
    snapshot = { ...snapshot, ...next };
    listeners.forEach((listener) => listener(snapshot));
  };

  return {
    isAvailable: () => Promise.resolve(Ctor !== undefined),
    start(languageTag) {
      if (Ctor === undefined) {
        return Promise.reject(new SpeechInputError('unavailable'));
      }
      if (recognition !== null) {
        return Promise.reject(new SpeechInputError('busy'));
      }
      const r = new Ctor();
      r.lang = languageTag;
      r.continuous = true;
      r.interimResults = true;
      r.onresult = (event) => {
        let text = '';
        for (let i = 0; i < event.results.length; i++) {
          text += event.results[i]?.[0].transcript ?? '';
        }
        update({ transcript: text.trim() });
      };
      r.onerror = (event) => {
        // Silence is not an error for the user: listening just ends.
        if (event.error !== 'no-speech' && event.error !== 'aborted') {
          update({ error: failure(event.error) });
        }
      };
      r.onend = () => {
        recognition = null;
        update({ listening: false });
        ended?.();
        ended = null;
      };
      recognition = r;
      snapshot = { listening: true, transcript: '', error: null };
      try {
        r.start();
      } catch {
        recognition = null;
        snapshot = { listening: false, transcript: '', error: null };
        return Promise.reject(new SpeechInputError('failed'));
      }
      update({});
      return Promise.resolve();
    },
    watch(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    stop() {
      const r = recognition;
      if (r === null) {
        return Promise.resolve(snapshot.transcript);
      }
      return new Promise<string>((resolve) => {
        ended = () => resolve(snapshot.transcript);
        r.stop();
      });
    },
    cancel() {
      recognition?.abort();
    },
  };
}
