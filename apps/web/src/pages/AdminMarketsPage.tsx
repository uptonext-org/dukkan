import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { MarketStatus, MerchantMarket } from '../api/types';
import { errorMessage } from '../ui/errors';

const STATUSES: MarketStatus[] = ['PENDING_REVIEW', 'ACTIVE', 'SUSPENDED'];

/**
 * Market review for admins (B-36): approve new markets, suspend or return them to review. Approval
 * needs complete seller information (imprint, Art. 30 DSA); the checklist shows what is missing.
 */
export function AdminMarketsPage() {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<MarketStatus | null>('PENDING_REVIEW');
  const markets = useQuery({ queryKey: ['adminMarkets', status, i18n.language], queryFn: () => api.adminMarkets(status), placeholderData: keepPreviousData });
  const setMarketStatus = useMutation({
    mutationFn: ({ market, next }: { market: MerchantMarket; next: MarketStatus }) => api.adminSetMarketStatus(market.id, next),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['adminMarkets'] }),
  });

  return (
    <section className="stack" aria-labelledby="admin-title">
      <h1 id="admin-title">{t('admin.title')}</h1>
      <label className="field" style={{ maxWidth: 240 }}>
        {t('admin.filter')}
        <select value={status ?? ''} onChange={(e) => setStatus(e.target.value === '' ? null : (e.target.value as MarketStatus))}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {t(`status.${s}`)}
            </option>
          ))}
          <option value="">{t('admin.all')}</option>
        </select>
      </label>
      {markets.isPending && <p>{t('app.loading')}</p>}
      {markets.isError && (
        <p className="error" role="alert">
          {errorMessage(t, markets.error)}
        </p>
      )}
      {setMarketStatus.isError && (
        <p className="error" role="alert">
          {errorMessage(t, setMarketStatus.error)}
        </p>
      )}
      {markets.data?.length === 0 && <p className="muted">{t('admin.empty')}</p>}
      {markets.data?.map((market) => (
        <article key={market.id} className="card stack" aria-label={market.name}>
          <div className="row">
            <h2 style={{ margin: 0 }}>{market.name}</h2>
            <span className={`badge ${market.status}`}>{t(`status.${market.status}`)}</span>
          </div>
          <div className="muted small">
            {market.type.name} · {market.addressLine}
          </div>
          <div className="small">
            {t('admin.legal')}: {market.imprint.legalName}
            {market.imprint.registerInfo != null && ` · ${market.imprint.registerInfo}`}
            {market.phone != null && ` · ${t('admin.phone')}: ${market.phone}`}
          </div>
          {market.review && (
            <ul className="small checklist" aria-label={t('admin.checklist')}>
              {market.review.items.map((item) => (
                <li key={item.code} className={item.ok ? undefined : item.required ? 'error' : 'muted'}>
                  {item.ok ? '✓' : '✗'} {t(`admin.check.${item.code}`)}
                  {!item.required && ` (${t('admin.optional')})`}: {t(item.ok ? 'admin.ok' : 'admin.missing')}
                </li>
              ))}
            </ul>
          )}
          {market.review?.complete === false && <p className="small error">{t('admin.incomplete')}</p>}
          <div className="row">
            {market.status !== 'ACTIVE' && (
              <button
                className="button"
                type="button"
                disabled={setMarketStatus.isPending || market.review?.complete === false}
                onClick={() => setMarketStatus.mutate({ market, next: 'ACTIVE' })}
              >
                {t('admin.approve')}
              </button>
            )}
            {market.status !== 'SUSPENDED' && (
              <button className="button danger" type="button" disabled={setMarketStatus.isPending} onClick={() => setMarketStatus.mutate({ market, next: 'SUSPENDED' })}>
                {t('admin.suspend')}
              </button>
            )}
            {market.status !== 'PENDING_REVIEW' && (
              <button className="button secondary" type="button" disabled={setMarketStatus.isPending} onClick={() => setMarketStatus.mutate({ market, next: 'PENDING_REVIEW' })}>
                {t('admin.review')}
              </button>
            )}
          </div>
        </article>
      ))}
    </section>
  );
}
