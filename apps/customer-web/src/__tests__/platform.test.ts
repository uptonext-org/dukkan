import { describe, expect, it, vi } from 'vitest';

import { readConfig } from '../config';
import { fitWithin } from '../platform/files';
import { createBrowserLocationService } from '../platform/location';
import { createBrowserPhotoPicker } from '../platform/photoPicker';
import { createLocalKeyValueStorage, createLocalTokenStorage } from '../platform/storage';
import { frameTimes } from '../platform/videoFrames';

describe('runtime config', () => {
  it('uses the same origin and OpenStreetMap unless /config.js says otherwise', () => {
    expect(readConfig(undefined, 'https://dukkan.example')).toEqual({
      apiBaseUrl: 'https://dukkan.example/api/v1',
      mapTileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      mapAttribution: expect.stringContaining('OpenStreetMap') as string,
    });
    expect(readConfig({ apiBaseUrl: 'https://api.example/api/v1/', mapTileUrl: ' ' }, 'https://x').apiBaseUrl).toBe(
      'https://api.example/api/v1',
    );
    expect(readConfig({ mapTileUrl: ' ' }, 'https://x').mapTileUrl).toContain('openstreetmap');
  });
});

describe('browser storage', () => {
  it('keeps the session tokens and reads a bare token of an older format', async () => {
    const tokens = createLocalTokenStorage(window.localStorage);
    await tokens.set({ accessToken: 'a', refreshToken: 'r' });
    expect(await tokens.get()).toEqual({ accessToken: 'a', refreshToken: 'r' });
    window.localStorage.setItem('dukkan.session', 'bare');
    expect(await tokens.get()).toEqual({ accessToken: 'bare', refreshToken: null });
    await tokens.clear();
    expect(await tokens.get()).toBeNull();
  });

  it('treats a blocked store as empty instead of failing', async () => {
    const blocked = {
      getItem: () => {
        throw new DOMException('denied', 'SecurityError');
      },
      setItem: () => {
        throw new DOMException('denied', 'SecurityError');
      },
      removeItem: () => undefined,
    } as unknown as Storage;
    const prefs = createLocalKeyValueStorage(blocked);
    await expect(prefs.set('k', 'v')).resolves.toBeUndefined();
    expect(await prefs.get('k')).toBeNull();
    expect(await createLocalTokenStorage(blocked).get()).toBeNull();
  });
});

describe('location', () => {
  it('maps the browser errors to the app reasons', async () => {
    const fail = (code: number) =>
      ({
        getCurrentPosition: (_ok: PositionCallback, error: PositionErrorCallback) =>
          error({ code, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3, message: '' }),
      }) as unknown as Geolocation;
    await expect(createBrowserLocationService(fail(1)).getCurrentPosition()).rejects.toMatchObject({ reason: 'denied' });
    await expect(createBrowserLocationService(fail(3)).getCurrentPosition()).rejects.toMatchObject({ reason: 'timeout' });
    await expect(createBrowserLocationService(null).getCurrentPosition()).rejects.toMatchObject({ reason: 'unavailable' });
    const found = {
      getCurrentPosition: (ok: PositionCallback) => ok({ coords: { latitude: 53.55, longitude: 10.01 } } as GeolocationPosition),
    } as unknown as Geolocation;
    await expect(createBrowserLocationService(found).getCurrentPosition()).resolves.toEqual({ lat: 53.55, lng: 10.01 });
  });
});

describe('photos and video frames', () => {
  it('scales down to the long edge and never enlarges', () => {
    expect(fitWithin(4000, 3000, 2048)).toEqual({ width: 2048, height: 1536 });
    expect(fitWithin(800, 600, 2048)).toEqual({ width: 800, height: 600 });
  });

  it('spreads frames over the clip away from its ends', () => {
    expect(frameTimes(20, 4)).toEqual([2.5, 7.5, 12.5, 17.5]);
    expect(frameTimes(Number.NaN, 4)).toEqual([]);
  });

  it('reports a cancelled chooser and an image the browser cannot read', async () => {
    expect(await createBrowserPhotoPicker(() => Promise.resolve(null)).pick('library')).toEqual({ status: 'cancelled' });
    vi.stubGlobal('createImageBitmap', () => Promise.reject(new Error('HEIC')));
    const unreadable = new File(['x'], 'a.heic', { type: 'image/heic' });
    expect(await createBrowserPhotoPicker(() => Promise.resolve(unreadable)).pick('camera')).toEqual({
      status: 'failed',
      reason: 'other',
    });
    vi.unstubAllGlobals();
  });
});
