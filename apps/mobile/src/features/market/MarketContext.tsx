import { createContext, use } from 'react';

export interface MarketContextValue {
  marketId: string;
  /** Leaves the sub-app back to the marketplace. */
  exit: () => void;
}

export const MarketContext = createContext<MarketContextValue | null>(null);

export function useMarketContext(): MarketContextValue {
  const value = use(MarketContext);
  if (value === null) {
    throw new Error('useMarketContext must be used inside the market sub-app.');
  }
  return value;
}
