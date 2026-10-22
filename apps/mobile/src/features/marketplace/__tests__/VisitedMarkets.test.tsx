import { act, render, waitFor } from '@testing-library/react-native';
import { useEffect } from 'react';

import { STORAGE_KEYS, type KeyValueStorage } from '../../../shared/storage';
import { useVisitedMarkets, VisitedMarketsProvider } from '../visits/VisitedMarkets';

function VisitOnMount({ marketId }: { marketId: string }) {
  const { recordVisit } = useVisitedMarkets();
  useEffect(() => recordVisit(marketId), [marketId, recordVisit]);
  return null;
}

describe('VisitedMarketsProvider', () => {
  it('keeps the stored history when a market opens before it has loaded (cold start from a link)', async () => {
    const written: string[] = [];
    let release: (raw: string) => void = () => undefined;
    const storage = {
      get: () =>
        new Promise<string | null>((resolve) => {
          release = resolve;
        }),
      set: (_key: string, value: string) => {
        written.push(value);
        return Promise.resolve();
      },
      remove: () => Promise.resolve(),
    } as unknown as KeyValueStorage;
    const now = () => new Date('2026-10-22T10:00:00Z');

    await render(
      <VisitedMarketsProvider storage={storage} now={now}>
        <VisitOnMount marketId="m-new" />
      </VisitedMarketsProvider>,
    );
    // Nothing is written while the stored list is still loading.
    expect(written).toEqual([]);

    await act(async () => {
      release(JSON.stringify([{ marketId: 'm-old', at: '2026-10-01T10:00:00Z' }]));
      await Promise.resolve();
    });
    await waitFor(() => expect(written.length).toBeGreaterThan(0));
    expect(JSON.parse(written[written.length - 1] ?? '[]')).toEqual([
      { marketId: 'm-new', at: '2026-10-22T10:00:00.000Z' },
      { marketId: 'm-old', at: '2026-10-01T10:00:00Z' },
    ]);
    expect(STORAGE_KEYS.visitedMarkets).toBeDefined();
  });
});
