import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState, type SyntheticEvent } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { Money, Promotion, PromotionType } from '../api/types';
import { useMerchantLanguages } from '../api/useMerchantLanguages';
import { LANGUAGES } from '../i18n';
import { errorMessage } from '../ui/errors';
import { formatMoney, parsePrice } from '../ui/format';

/**
 * Promotions of the market: percent off, quantity prices (3 for 5 €, 7 for 10 €) or an offer price
 * per product, each for a period and optionally only on some weekdays and hours.
 */
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
                      <bdi>{offerLabel(t, p, i18n.language)}</bdi>
                    </td>
                    <td>
                      {active ? <bdi>{new Date(p.validUntil).toLocaleDateString(i18n.language === 'ar' ? 'ar-u-nu-latn' : i18n.language)}</bdi> : <span className="muted">{t('promotions.ended')}</span>}
                      {scheduleLabel(t, p, i18n.language) !== null && <div className="muted small">{scheduleLabel(t, p, i18n.language)}</div>}
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

/** Weekday names Monday..Sunday (ISO 1..7) in the UI language. */
function weekdayNames(language: string): string[] {
  const format = new Intl.DateTimeFormat(language === 'ar' ? 'ar-u-nu-latn' : language, { weekday: 'short', timeZone: 'UTC' });
  // 1 January 2024 was a Monday.
  return [0, 1, 2, 3, 4, 5, 6].map((i) => format.format(new Date(Date.UTC(2024, 0, 1 + i))));
}

function offerLabel(t: TFunction, p: Promotion, language: string): string {
  if (p.type === 'PERCENT_OFF') {
    return t('promotions.percentLabel', { percent: p.percentOff ?? 0 });
  }
  if (p.type === 'FIXED_PRICE') {
    return t('promotions.offerPrices', { count: p.offers?.length ?? p.productIds.length });
  }
  const tiers = p.tiers !== undefined && p.tiers.length > 0 ? p.tiers : p.bundleQuantity != null && p.bundlePrice != null ? [{ quantity: p.bundleQuantity, price: p.bundlePrice }] : [];
  return tiers.map((tier) => t('promotions.bundleLabel', { quantity: tier.quantity, price: formatMoney(tier.price, language) })).join(' · ');
}

function scheduleLabel(t: TFunction, p: Promotion, language: string): string | null {
  const parts: string[] = [];
  if (p.weekdays != null && p.weekdays.length < 7) {
    const names = weekdayNames(language);
    parts.push(p.weekdays.map((d) => names[d - 1]).join(', '));
  }
  if (p.dailyFrom != null && p.dailyUntil != null) {
    parts.push(t('promotions.dailyLabel', { from: p.dailyFrom, until: p.dailyUntil }));
  }
  return parts.length === 0 ? null : parts.join(' · ');
}

/** `yyyy-mm-dd` of a local date. */
function isoDate(date: Date): string {
  return `${String(date.getFullYear())}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

interface TierInput {
  quantity: string;
  price: string;
}

function PromotionForm({ marketId, primaryLocale }: { marketId: string; primaryLocale: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const { languages, multilingual } = useMerchantLanguages(primaryLocale);
  const [titles, setTitles] = useState<Record<string, string>>({});
  const [type, setType] = useState<PromotionType>('PERCENT_OFF');
  const [percent, setPercent] = useState('20');
  const [tiers, setTiers] = useState<TierInput[]>([{ quantity: '3', price: '' }]);
  const [offers, setOffers] = useState<Record<string, string>>({});
  const [startDate, setStartDate] = useState(() => isoDate(new Date()));
  const [endDate, setEndDate] = useState(() => isoDate(new Date(Date.now() + 6 * 24 * 60 * 60 * 1000)));
  const [weekdays, setWeekdays] = useState<Set<number>>(new Set([1, 2, 3, 4, 5, 6, 7]));
  const [allDay, setAllDay] = useState(true);
  const [dailyFrom, setDailyFrom] = useState('08:00');
  const [dailyUntil, setDailyUntil] = useState('12:00');
  const [selected, setSelected] = useState<Map<string, Money>>(new Map());
  const [search, setSearch] = useState('');
  const q = useDeferredValue(search);
  const [problem, setProblem] = useState<string | null>(null);
  const [created, setCreated] = useState(false);
  const names = weekdayNames(i18n.language);
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
      const today = isoDate(new Date());
      const from = startDate <= today ? new Date() : new Date(`${startDate}T00:00`);
      const until = new Date(`${endDate}T00:00`);
      until.setDate(until.getDate() + 1);
      const ids = [...selected.keys()];
      return api.createPromotion(marketId, {
        title: Object.fromEntries(Object.entries(titles).map(([k, v]): [string, string] => [k, v.trim()]).filter(([, v]) => v !== '')),
        type,
        percentOff: type === 'PERCENT_OFF' ? Number(percent) : null,
        bundleQuantity: null,
        bundlePrice: null,
        tiers: type === 'MULTI_BUY' ? tiers.map((tier) => ({ quantity: Number(tier.quantity), price: { amountCents: parsePrice(tier.price) ?? 0, currency: 'EUR' as const } })) : null,
        offers: type === 'FIXED_PRICE' ? ids.map((id) => ({ productId: id, offerPrice: { amountCents: parsePrice(offers[id] ?? '') ?? 0, currency: 'EUR' as const } })) : null,
        validFrom: from.toISOString(),
        validUntil: until.toISOString(),
        weekdays: weekdays.size === 7 ? null : [...weekdays].sort((a, b) => a - b),
        dailyFrom: allDay ? null : dailyFrom,
        dailyUntil: allDay ? null : dailyUntil,
        productIds: ids,
      });
    },
    onSuccess: () => {
      setTitles({});
      setSelected(new Map());
      setOffers({});
      setCreated(true);
      void queryClient.invalidateQueries({ queryKey: ['promotions', marketId] });
    },
  });
  const filledTitles = LANGUAGES.filter((l) => (titles[l.code] ?? '').trim() !== '');

  const tiersProblem = (): boolean => {
    const quantities = tiers.map((tier) => Number(tier.quantity));
    return (
      tiers.some((tier) => !(Number.isInteger(Number(tier.quantity)) && Number(tier.quantity) >= 2 && Number(tier.quantity) <= 99) || parsePrice(tier.price) === null) ||
      new Set(quantities).size !== quantities.length
    );
  };
  const offersProblem = (): boolean =>
    [...selected.entries()].some(([id, price]) => {
      const offer = parsePrice(offers[id] ?? '');
      return offer === null || offer >= price.amountCents;
    });

  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    setCreated(false);
    const percentValue = Number(percent);
    if ((titles[primaryLocale] ?? '').trim() === '') {
      setProblem(t('promotions.titleRequired'));
    } else if (type === 'PERCENT_OFF' && !(Number.isInteger(percentValue) && percentValue >= 1 && percentValue <= 90)) {
      setProblem(t('promotions.percentInvalid'));
    } else if (type === 'MULTI_BUY' && tiersProblem()) {
      setProblem(t('promotions.tiersInvalid'));
    } else if (selected.size === 0) {
      setProblem(t('promotions.productsRequired'));
    } else if (type === 'FIXED_PRICE' && offersProblem()) {
      setProblem(t('promotions.offersInvalid'));
    } else if (endDate < startDate || endDate < isoDate(new Date())) {
      setProblem(t('promotions.datesInvalid'));
    } else if (weekdays.size === 0) {
      setProblem(t('promotions.weekdaysRequired'));
    } else if (!allDay && !(dailyFrom < dailyUntil)) {
      setProblem(t('promotions.dailyInvalid'));
    } else {
      setProblem(null);
      create.mutate();
    }
  };

  return (
    <form className="card stack" onSubmit={submit} aria-labelledby="new-promotion">
      <h2 id="new-promotion">{t('promotions.new')}</h2>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        {languages.map((l) => (
          <label key={l.code} className="field" style={{ flex: '1 1 200px' }}>
            {multilingual ? `${t('promotions.titleLabel')} · ${l.nativeName}${l.code === primaryLocale ? ` (${t('promotions.primary')})` : ''}` : t('promotions.titleLabel')}
            <input dir={l.direction} lang={l.code} value={titles[l.code] ?? ''} onChange={(e) => setTitles({ ...titles, [l.code]: e.target.value })} />
          </label>
        ))}
        {multilingual && filledTitles.length > 0 && filledTitles.length < LANGUAGES.length && (
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
            <option value="FIXED_PRICE">{t('promotions.typeFixed')}</option>
          </select>
        </label>
        {type === 'PERCENT_OFF' && (
          <label className="field">
            {t('promotions.percent')}
            <input inputMode="numeric" dir="ltr" value={percent} onChange={(e) => setPercent(e.target.value)} style={{ width: '6em' }} />
          </label>
        )}
      </div>
      {type === 'MULTI_BUY' && (
        <fieldset className="stack tight" style={{ border: 0, padding: 0, margin: 0 }}>
          <legend className="small muted">{t('promotions.tiersHint')}</legend>
          {tiers.map((tier, index) => (
            <div key={index} className="row" style={{ alignItems: 'flex-end' }}>
              <label className="field">
                {t('promotions.tierQuantity', { n: index + 1 })}
                <input
                  inputMode="numeric"
                  dir="ltr"
                  value={tier.quantity}
                  onChange={(e) => setTiers(tiers.map((x, i) => (i === index ? { ...x, quantity: e.target.value } : x)))}
                  style={{ width: '6em' }}
                />
              </label>
              <label className="field">
                {t('promotions.tierPrice', { n: index + 1 })}
                <input
                  inputMode="decimal"
                  dir="ltr"
                  value={tier.price}
                  onChange={(e) => setTiers(tiers.map((x, i) => (i === index ? { ...x, price: e.target.value } : x)))}
                  style={{ width: '8em' }}
                />
              </label>
              {tiers.length > 1 && (
                <button className="button secondary" type="button" onClick={() => setTiers(tiers.filter((_, i) => i !== index))}>
                  {t('promotions.removeTier', { n: index + 1 })}
                </button>
              )}
            </div>
          ))}
          {tiers.length < 5 && (
            <div>
              <button className="button secondary" type="button" onClick={() => setTiers([...tiers, { quantity: '', price: '' }])}>
                {t('promotions.addTier')}
              </button>
            </div>
          )}
        </fieldset>
      )}
      <fieldset className="stack tight" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="small muted">{t('promotions.when')}</legend>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            {t('promotions.from')}
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          </label>
          <label className="field">
            {t('promotions.untilDate')}
            <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          </label>
        </div>
        <div className="row" role="group" aria-label={t('promotions.weekdays')}>
          {names.map((name, i) => (
            <label key={name} className="chip">
              <input
                type="checkbox"
                checked={weekdays.has(i + 1)}
                onChange={(e) => {
                  const next = new Set(weekdays);
                  if (e.target.checked) {
                    next.add(i + 1);
                  } else {
                    next.delete(i + 1);
                  }
                  setWeekdays(next);
                }}
              />
              {name}
            </label>
          ))}
        </div>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="chip" style={{ alignSelf: 'center' }}>
            <input type="checkbox" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} />
            {t('promotions.allDay')}
          </label>
          {!allDay && (
            <>
              <label className="field">
                {t('promotions.dailyFrom')}
                <input type="time" value={dailyFrom} onChange={(e) => setDailyFrom(e.target.value)} />
              </label>
              <label className="field">
                {t('promotions.dailyUntil')}
                <input type="time" value={dailyUntil} onChange={(e) => setDailyUntil(e.target.value)} />
              </label>
            </>
          )}
        </div>
      </fieldset>
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
                        const next = new Map(selected);
                        if (e.target.checked) {
                          next.set(l.id, l.price);
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
                  {type === 'FIXED_PRICE' && (
                    <td>
                      {selected.has(l.id) && (
                        <input
                          className="price-input"
                          inputMode="decimal"
                          dir="ltr"
                          aria-label={t('promotions.offerPriceFor', { name: l.name })}
                          placeholder={t('promotions.offerPrice')}
                          value={offers[l.id] ?? ''}
                          onChange={(e) => setOffers({ ...offers, [l.id]: e.target.value })}
                        />
                      )}
                    </td>
                  )}
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
