import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { Promotion, PromotionType } from '../api/types';
import { LANGUAGES } from '../i18n';
import { errorMessage } from '../ui/errors';
import { formatMoney, parsePrice } from '../ui/format';

const DURATIONS = [3, 7, 14, 30];

/** Promotions of the market (percent off or multi-buy): list, end, create with a product picker. */
export function PromotionsPage({ marketId, primaryLocale }: { marketId: string; primaryLocale: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const promotions = useQuery({
    queryKey: ['promotions', marketId, i18n.language],
    queryFn: () => api.promotions(marketId),
    placeholderData: keepPreviousData,
  });
  const end = useMutation({
    mutationFn: (promotion: Promotion) => api.endPromotion(marketId, promotion.id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['promotions', marketId] }),
  });
  // When the list was loaded; an "active" promotion ends at validUntil.
  const now = promotions.dataUpdatedAt;

  return (
    <section className="stack" aria-labelledby="promotions-title">
      <h1 id="promotions-title">{t('promotions.title')}</h1>
      <PromotionForm marketId={marketId} primaryLocale={primaryLocale} />
      <h2>{t('promotions.list')}</h2>
      {promotions.isPending && <p>{t('app.loading')}</p>}
      {promotions.isError && (
        <p className="error" role="alert">
          {errorMessage(t, promotions.error)}
        </p>
      )}
      {end.isError && (
        <p className="error" role="alert">
          {errorMessage(t, end.error)}
        </p>
      )}
      {promotions.data?.length === 0 && <p className="muted">{t('promotions.empty')}</p>}
      {promotions.data !== undefined && promotions.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">{t('promotions.titleLabel')}</th>
                <th scope="col">{t('promotions.offer')}</th>
                <th scope="col">{t('promotions.until')}</th>
                <th scope="col">{t('promotions.products')}</th>
                <th scope="col">
                  <span className="visually-hidden">{t('promotions.end')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {promotions.data.map((p) => {
                const active = new Date(p.validUntil).getTime() > now;
                return (
                  <tr key={p.id}>
                    <th scope="row">{p.title}</th>
                    <td>
                      <bdi>
                        {p.type === 'PERCENT_OFF'
                          ? t('promotions.percentLabel', { percent: p.percentOff ?? 0 })
                          : t('promotions.bundleLabel', { quantity: p.bundleQuantity ?? 0, price: p.bundlePrice == null ? '' : formatMoney(p.bundlePrice, i18n.language) })}
                      </bdi>
                    </td>
                    <td>
                      {active ? <bdi>{new Date(p.validUntil).toLocaleDateString(i18n.language === 'ar' ? 'ar-u-nu-latn' : i18n.language)}</bdi> : <span className="muted">{t('promotions.ended')}</span>}
                    </td>
                    <td>
                      <bdi>{p.productIds.length}</bdi>
                    </td>
                    <td>
                      {active && (
                        <button className="button danger" type="button" disabled={end.isPending} onClick={() => end.mutate(p)}>
                          {t('promotions.end')}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function PromotionForm({ marketId, primaryLocale }: { marketId: string; primaryLocale: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [titles, setTitles] = useState<Record<string, string>>({});
  const [type, setType] = useState<PromotionType>('PERCENT_OFF');
  const [percent, setPercent] = useState('20');
  const [quantity, setQuantity] = useState('3');
  const [bundlePrice, setBundlePrice] = useState('');
  const [days, setDays] = useState(7);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const q = useDeferredValue(search);
  const [problem, setProblem] = useState<string | null>(null);
  const [created, setCreated] = useState(false);
  const listings = useQuery({
    queryKey: ['listings', marketId, q, i18n.language, 'picker'],
    queryFn: () => api.listings(marketId, q, 0),
    placeholderData: keepPreviousData,
  });
  const translate = useMutation({
    mutationFn: () => api.translate('PROMOTION', Object.fromEntries(Object.entries(titles).filter(([, v]) => v.trim() !== ''))),
    onSuccess: (result) => {
      const suggestions = result.fields.find((f) => f.key === 'text')?.values ?? {};
      setTitles((current) => {
        const next = { ...current };
        for (const [code, text] of Object.entries(suggestions)) {
          if ((next[code] ?? '').trim() === '') {
            next[code] = text;
          }
        }
        return next;
      });
    },
  });
  const create = useMutation({
    mutationFn: () => {
      const from = new Date();
      const until = new Date(from.getTime() + days * 24 * 60 * 60 * 1000);
      const cents = parsePrice(bundlePrice);
      return api.createPromotion(marketId, {
        title: Object.fromEntries(Object.entries(titles).map(([k, v]): [string, string] => [k, v.trim()]).filter(([, v]) => v !== '')),
        type,
        percentOff: type === 'PERCENT_OFF' ? Number(percent) : null,
        bundleQuantity: type === 'MULTI_BUY' ? Number(quantity) : null,
        bundlePrice: type === 'MULTI_BUY' && cents !== null ? { amountCents: cents, currency: 'EUR' } : null,
        validFrom: from.toISOString(),
        validUntil: until.toISOString(),
        productIds: [...selected],
      });
    },
    onSuccess: () => {
      setTitles({});
      setSelected(new Set());
      setCreated(true);
      void queryClient.invalidateQueries({ queryKey: ['promotions', marketId] });
    },
  });
  const filledTitles = LANGUAGES.filter((l) => (titles[l.code] ?? '').trim() !== '');

  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    setCreated(false);
    const percentValue = Number(percent);
    const quantityValue = Number(quantity);
    if ((titles[primaryLocale] ?? '').trim() === '') {
      setProblem(t('promotions.titleRequired'));
    } else if (type === 'PERCENT_OFF' && !(Number.isInteger(percentValue) && percentValue >= 1 && percentValue <= 90)) {
      setProblem(t('promotions.percentInvalid'));
    } else if (type === 'MULTI_BUY' && (!(Number.isInteger(quantityValue) && quantityValue >= 2 && quantityValue <= 20) || parsePrice(bundlePrice) === null)) {
      setProblem(t('promotions.bundleInvalid'));
    } else if (selected.size === 0) {
      setProblem(t('promotions.productsRequired'));
    } else {
      setProblem(null);
      create.mutate();
    }
  };

  return (
    <form className="card stack" onSubmit={submit} aria-labelledby="new-promotion">
      <h2 id="new-promotion">{t('promotions.new')}</h2>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        {LANGUAGES.map((l) => (
          <label key={l.code} className="field" style={{ flex: '1 1 200px' }}>
            {`${t('promotions.titleLabel')} · ${l.nativeName}${l.code === primaryLocale ? ` (${t('promotions.primary')})` : ''}`}
            <input dir={l.direction} lang={l.code} value={titles[l.code] ?? ''} onChange={(e) => setTitles({ ...titles, [l.code]: e.target.value })} />
          </label>
        ))}
        {filledTitles.length > 0 && filledTitles.length < LANGUAGES.length && (
          <button className="button secondary" type="button" disabled={translate.isPending} onClick={() => translate.mutate()}>
            {t('promotions.translate')}
          </button>
        )}
      </div>
      {translate.isError && (
        <p className="error small" role="alert">
          {errorMessage(t, translate.error)}
        </p>
      )}
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          {t('promotions.type')}
          <select value={type} onChange={(e) => setType(e.target.value as PromotionType)}>
            <option value="PERCENT_OFF">{t('promotions.typePercent')}</option>
            <option value="MULTI_BUY">{t('promotions.typeBundle')}</option>
          </select>
        </label>
        {type === 'PERCENT_OFF' ? (
          <label className="field">
            {t('promotions.percent')}
            <input inputMode="numeric" dir="ltr" value={percent} onChange={(e) => setPercent(e.target.value)} style={{ width: '6em' }} />
          </label>
        ) : (
          <>
            <label className="field">
              {t('promotions.quantity')}
              <input inputMode="numeric" dir="ltr" value={quantity} onChange={(e) => setQuantity(e.target.value)} style={{ width: '6em' }} />
            </label>
            <label className="field">
              {t('promotions.bundlePrice')}
              <input inputMode="decimal" dir="ltr" value={bundlePrice} onChange={(e) => setBundlePrice(e.target.value)} style={{ width: '8em' }} />
            </label>
          </>
        )}
        <label className="field">
          {t('promotions.duration')}
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {DURATIONS.map((d) => (
              <option key={d} value={d}>
                {t('promotions.days', { count: d })}
              </option>
            ))}
          </select>
        </label>
      </div>
      <fieldset className="stack" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="small muted">{t('promotions.pickProducts', { count: selected.size })}</legend>
        <label className="field" style={{ maxWidth: 320 }}>
          {t('listings.search')}
          <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <div className="table-wrap" style={{ maxHeight: 280, overflowY: 'auto' }}>
          <table>
            <tbody>
              {(listings.data?.items ?? []).map((l) => (
                <tr key={l.id}>
                  <td style={{ width: 40 }}>
                    <input
                      type="checkbox"
                      aria-label={l.name}
                      checked={selected.has(l.id)}
                      onChange={(e) => {
                        const next = new Set(selected);
                        if (e.target.checked) {
                          next.add(l.id);
                        } else {
                          next.delete(l.id);
                        }
                        setSelected(next);
                      }}
                    />
                  </td>
                  <td>{l.name}</td>
                  <td>
                    <bdi>{l.unitSize}</bdi>
                  </td>
                  <td>
                    <bdi>{formatMoney(l.price, i18n.language)}</bdi>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </fieldset>
      {problem !== null && (
        <p className="error" role="alert">
          {problem}
        </p>
      )}
      {create.isError && (
        <p className="error" role="alert">
          {errorMessage(t, create.error)}
        </p>
      )}
      {created && (
        <p className="success" role="status">
          {t('promotions.created')}
        </p>
      )}
      <div>
        <button className="button" type="submit" disabled={create.isPending}>
          {t('promotions.create')}
        </button>
      </div>
    </form>
  );
}
