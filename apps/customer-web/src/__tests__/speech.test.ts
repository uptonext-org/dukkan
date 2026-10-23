import { describe, expect, it } from 'vitest';

import { createBrowserSpeechInput, type Recognition } from '../platform/speech';

class FakeRecognition implements Recognition {
  static last: FakeRecognition | null = null;
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: Recognition['onresult'] = null;
  onerror: Recognition['onerror'] = null;
  onend: Recognition['onend'] = null;
  constructor() {
    FakeRecognition.last = this;
  }
  start() {}
  stop() {
    this.onend?.();
  }
  abort() {
    this.onend?.();
  }
  hear(...parts: string[]) {
    const results = Object.assign(parts.map((transcript) => ({ 0: { transcript } })), { length: parts.length });
    this.onresult?.({ results });
  }
}

describe('browser dictation', () => {
  it('streams the transcript and returns it when stopped', async () => {
    const speech = createBrowserSpeechInput(FakeRecognition);
    const seen: string[] = [];
    speech.watch((s) => seen.push(`${s.listening}:${s.transcript}`));
    await speech.start('ar-SA');
    const recognition = FakeRecognition.last!;
    expect([recognition.lang, recognition.continuous, recognition.interimResults]).toEqual(['ar-SA', true, true]);
    recognition.hear('2 Gurken');
    recognition.hear('2 Gurken', ' und Milch');
    await expect(speech.stop()).resolves.toBe('2 Gurken und Milch');
    expect(seen).toEqual(['true:', 'true:2 Gurken', 'true:2 Gurken und Milch', 'false:2 Gurken und Milch']);
  });

  it('reports a blocked microphone, ignores silence and is unavailable without the API', async () => {
    const speech = createBrowserSpeechInput(FakeRecognition);
    const errors: (string | null)[] = [];
    speech.watch((s) => errors.push(s.error));
    await speech.start('de-DE');
    FakeRecognition.last!.onerror?.({ error: 'no-speech' });
    FakeRecognition.last!.onerror?.({ error: 'not-allowed' });
    expect(errors.at(-1)).toBe('permission');
    await expect(speech.start('de-DE')).rejects.toMatchObject({ reason: 'busy' });

    const none = createBrowserSpeechInput(undefined);
    expect(await none.isAvailable()).toBe(false);
    await expect(none.start('de-DE')).rejects.toMatchObject({ reason: 'unavailable' });
  });
});
