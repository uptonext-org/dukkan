import { keepPreviousData, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { Listing } from '../api/types';
import { errorMessage } from '../ui/errors';
import { centsToInput, parsePrice } from '../ui/format';

export function ListingsPage({ marketId }: { marketId: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const [search, setSearch] = useState('');
  const q = useDeferredValue(search);
  const listings = useInfiniteQuery({
    queryKey: ['listings', marketId, q, i18n.language],
    queryFn: ({ pageParam }) => api.listings(marketId, q, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    placeholderData: keepPreviousData,
  });
  const rows = listings.data?.pages.flatMap((p) => p.items) ?? [];
  const total = listings.data?.pages[0]?.totalItems ?? 0;

  return (
    <section className="stack" aria-labelledby="listings-title">
      <h1 id="listings-title">{t('listings.title')}</h1>
      <div className="row">
        <label className="field">
          {t('listings.search')}
          <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        {listings.data !== undefined && <span className="muted small">{t('listings.count', { count: total })}</span>}
      </div>
      {listings.isPending && <p>{t('app.loading')}</p>}
      {listings.isError && (
        <p className="error" role="alert">
          {errorMessage(t, listings.error)}
        </p>
      )}
      {listings.data !== undefined && rows.length === 0 && <p className="muted">{t('listings.empty')}</p>}
      {rows.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">{t('listings.name')}</th>
                <th scope="col">{t('listings.size')}</th>
                <th scope="col">{t('listings.price')}</th>
                <th scope="col">{t('listings.available')}</th>
                <th scope="col">
                  <span className="visually-hidden">{t('listings.save')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((listing) => (
                <ListingRow key={`${listing.id}-${listing.price.amountCents}-${String(listing.available)}`} marketId={marketId} listing={listing} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {listings.hasNextPage && (
        <button className="button secondary" type="button" disabled={listings.isFetchingNextPage} onClick={() => void listings.fetchNextPage()}>
          {t('listings.more')}
        </button>
      )}
    </section>
  );
}

/** One editable row; keyed by its saved values, so it resets after a save. */
function ListingRow({ marketId, listing }: { marketId: string; listing: Listing }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [price, setPrice] = useState(() => centsToInput(listing.price.amountCents, i18n.language));
  const [available, setAvailable] = useState(listing.available);
  const save = useMutation({
    mutationFn: (update: { price?: { amountCents: number; currency: 'EUR' }; available?: boolean }) => api.updateListing(marketId, listing.id, update),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['listings', marketId] }),
  });
  const cents = parsePrice(price);
  const changed = cents !== listing.price.amountCents || available !== listing.available;
  const label = listing.brand != null ? `${listing.name} · ${listing.brand}` : listing.name;

  return (
    <tr>
      <th scope="row">
        {label}
        {listing.privateProduct && <div className="muted small">{t('listings.own')}</div>}
      </th>
      <td>
        <bdi>{listing.unitSize}</bdi>
      </td>
      <td>
        <input
          className="price-input"
          inputMode="decimal"
          dir="ltr"
          aria-label={`${t('listings.price')}: ${listing.name}`}
          aria-invalid={cents === null}
          value={price}
          onChange={(e) => setPrice(e.target.value)}
        />
        {cents === null && <div className="error small">{t('listings.invalidPrice')}</div>}
      </td>
      <td>
        <input type="checkbox" aria-label={`${t('listings.available')}: ${listing.name}`} checked={available} onChange={(e) => setAvailable(e.target.checked)} />
      </td>
      <td>
        <button
          className="button"
          type="button"
          disabled={!changed || cents === null || save.isPending}
          onClick={() =>
            save.mutate({
              ...(cents !== null && cents !== listing.price.amountCents ? { price: { amountCents: cents, currency: 'EUR' as const } } : {}),
              ...(available !== listing.available ? { available } : {}),
            })
          }>
          {t('listings.save')}
        </button>
        {save.isError && (
          <div className="error small" role="alert">
            {errorMessage(t, save.error)}
          </div>
        )}
      </td>
    </tr>
  );
}
