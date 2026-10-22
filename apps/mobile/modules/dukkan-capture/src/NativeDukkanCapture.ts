import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

/**
 * Speech is recognised on the device (Android SpeechRecognizer, iOS SFSpeechRecognizer); only the
 * text leaves the phone. Listening keeps going across pauses until stopSpeech or cancelSpeech.
 * The live transcript and state are read by polling (no events), which keeps the module small.
 */
export interface Spec extends TurboModule {
  isSpeechAvailable(): Promise<boolean>;
  /** Rejects with code `permission`, `unavailable`, `busy` or `failed`. */
  startSpeech(languageTag: string): Promise<void>;
  isListening(): boolean;
  /** Everything recognised so far, including the current partial phrase. */
  speechTranscript(): string;
  /** Empty, or the last recognition error (`permission`, `network`, `failed`). */
  speechError(): string;
  /** Stops listening and resolves with the final transcript. */
  stopSpeech(): Promise<string>;
  cancelSpeech(): void;
  /** JPEG frames spread over the video, at most `maxEdge` px on the long side, as file:// URIs. */
  extractVideoFrames(uri: string, count: number, maxEdge: number): Promise<string[]>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('DukkanCapture');
