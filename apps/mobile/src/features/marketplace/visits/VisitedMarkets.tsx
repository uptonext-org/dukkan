import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { STORAGE_KEYS, type KeyValueStorage } from '../../../shared/storage';

export interface MarketVisit {
  marketId: string;
  /** ISO time of the last visit. */
  at: string;
}

export const MAX_VISITS = 20;

/** The visit list after opening a market: that market first, each market once, at most 20. */
export function addVisit(visits: MarketVisit[], marketId: string, at: string): MarketVisit[] {
  return [{ marketId, at }, ...visits.filter((v) => v.marketId !== marketId)].slice(0, MAX_VISITS);
}

function parse(raw: string | null): MarketVisit[] {
  if (raw === null) {
    return [];
  }
  try {
    const value: unknown = JSON.parse(raw);
    return Array.isArray(value)
      ? value.filter((v): v is MarketVisit => typeof v === 'object' && v !== null && typeof (v as MarketVisit).marketId === 'string' && typeof (v as MarketVisit).at === 'string')
      : [];
  } catch {
    return [];
  }
}

interface VisitedMarkets {
  visits: MarketVisit[];
  recordVisit: (marketId: string) => void;
}

const Context = createContext<VisitedMarkets>({ visits: [], recordVisit: () => undefined });

const systemNow = () => new Date();

/** Remembers the markets opened on this device (also for guests) for the start page view "Meine Märkte". */
export function VisitedMarketsProvider({ storage, children, now = systemNow }: { storage: KeyValueStorage; children: ReactNode; now?: () => Date }) {
  const [state, setState] = useState<{ loaded: boolean; visits: MarketVisit[] }>({ loaded: false, visits: [] });
  useEffect(() => {
    let active = true;
    const merge = (stored: MarketVisit[]) =>
      setState((current) => ({
        loaded: true,
        // A visit recorded before the stored list arrived stays first.
        visits: current.visits.reduceRight((list, visit) => addVisit(list, visit.marketId, visit.at), stored),
      }));
    storage
      .get(STORAGE_KEYS.visitedMarkets)
      .then((raw) => active && merge(parse(raw)))
      .catch(() => active && merge([]));
    return () => {
      active = false;
    };
  }, [storage]);
  // Written only once the stored list is merged in, so an early visit cannot overwrite the history.
  useEffect(() => {
    if (state.loaded) {
      void storage.set(STORAGE_KEYS.visitedMarkets, JSON.stringify(state.visits));
    }
  }, [state, storage]);
  const recordVisit = useCallback(
    (marketId: string) => setState((current) => ({ ...current, visits: addVisit(current.visits, marketId, now().toISOString()) })),
    [now],
  );
  const visits = state.visits;
  const value = useMemo(() => ({ visits, recordVisit }), [visits, recordVisit]);
  return <Context value={value}>{children}</Context>;
}

export function useVisitedMarkets(): VisitedMarkets {
  return use(Context);
}
