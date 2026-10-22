import { createContext, use, type ReactNode } from 'react';

import type { ShoppingInput } from './ShoppingInput';

const ShoppingInputContext = createContext<ShoppingInput | null>(null);

export function ShoppingInputProvider({ input, children }: { input: ShoppingInput; children: ReactNode }) {
  return <ShoppingInputContext value={input}>{children}</ShoppingInputContext>;
}

export function useShoppingInput(): ShoppingInput {
  const input = use(ShoppingInputContext);
  if (input === null) {
    throw new Error('useShoppingInput must be used inside <ShoppingInputProvider>.');
  }
  return input;
}
