import { afterEach, describe, expect, it, vi } from 'vitest';

import { Alert, Share } from '../shims/react-native';

afterEach(() => vi.unstubAllGlobals());

describe('Alert on the web', () => {
  it('runs the action of a confirmed dialog and the cancel handler otherwise', () => {
    const clear = vi.fn();
    const keep = vi.fn();
    const buttons = [
      { text: 'Abbrechen', style: 'cancel' as const, onPress: keep },
      { text: 'Leeren', style: 'destructive' as const, onPress: clear },
    ];
    const confirm = vi.fn(() => true);
    vi.stubGlobal('confirm', confirm);
    Alert.alert('Warenkorb leeren?', 'Alle Artikel werden entfernt.', buttons);
    expect(confirm).toHaveBeenCalledWith('Warenkorb leeren?\n\nAlle Artikel werden entfernt.');
    expect(clear).toHaveBeenCalledOnce();

    vi.stubGlobal('confirm', vi.fn(() => false));
    Alert.alert('Warenkorb leeren?', undefined, buttons);
    expect(keep).toHaveBeenCalledOnce();
    expect(clear).toHaveBeenCalledOnce();
  });

  it('shows a single-button message with alert', () => {
    const ok = vi.fn();
    const alert = vi.fn();
    vi.stubGlobal('alert', alert);
    Alert.alert('Fehler', 'Nicht gespeichert', [{ text: 'OK', onPress: ok }]);
    expect(alert).toHaveBeenCalledWith('Fehler\n\nNicht gespeichert');
    expect(ok).toHaveBeenCalledOnce();
  });
});

describe('Share on the web', () => {
  it('downloads the data export where the browser has no share sheet', async () => {
    const clicked: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this.download);
    });
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => undefined }));
    await expect(Share.share({ title: 'Meine Daten', message: '{"a":1}' })).resolves.toEqual({ action: 'sharedAction' });
    expect(clicked).toEqual(['dukkan.json']);
  });
});
