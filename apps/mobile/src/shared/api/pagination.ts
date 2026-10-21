import type { Page } from './types';

/** `getNextPageParam` for the contract's zero-based pages. */
export function nextPage<T>(last: Page<T>): number | undefined {
  return last.page + 1 < last.totalPages ? last.page + 1 : undefined;
}

export function flattenPages<T>(data: { pages: Page<T>[] } | undefined): T[] {
  return data?.pages.flatMap((p) => p.items) ?? [];
}
