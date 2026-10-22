import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { InsightTotals, MarketInsights } from '../api/types';
import { ColumnChart } from '../ui/ColumnChart';
import { errorMessage } from '../ui/errors';
import { formatMoney } from '../ui/format';

export const PERIODS = [7, 30, 90] as const;

function locale(language: string): string {
  return language === 'ar' ? 'ar-u-nu-latn' : language;
}

/** Change against the period before in percent, or null when there is nothing to compare with. */
export function change(current: number, previous: number): number | null {
  if (previous === 0) {
    return null;
  }
  return Math.round(((current - previous) / previous) * 100);
}

/** Sales of the market: headline numbers against the period before, days, hours, best sellers. */
export function InsightsPage({ marketId }: { marketId: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const [days, setDays] = useState<number>(30);
  const insights = useQuery({
    queryKey: ['insights', marketId, days, i18n.language],
    queryFn: () => api.insights(marketId, days),
    placeholderData: keepPreviousData,
  });

  return (
    <section className="stack" aria-labelledby="insights-title">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1 id="insights-title" style={{ margin: 0 }}>
          {t('insights.title')}
        </h1>
        <div className="segmented" role="group" aria-label={t('insights.period')}>
          {PERIODS.map((p) => (
            <button key={p} type="button" aria-pressed={p === days} onClick={() => setDays(p)}>
              {t('insights.days', { count: p })}
            </button>
          ))}
        </div>
      </div>
      {insights.isPending && <p>{t('app.loading')}</p>}
      {insights.isError && (
        <p className="error" role="alert">
          {errorMessage(t, insights.error)}
        </p>
      )}
      {insights.data !== undefined && <Overview data={insights.data} />}
    </section>
  );
}

function Overview({ data }: { data: MarketInsights }) {
  const { t, i18n } = useTranslation();
  const language = i18n.language;
  const number = new Intl.NumberFormat(locale(language));
  const euro = (cents: number) => formatMoney({ amountCents: cents, currency: 'EUR' }, language);
  const compactEuro = (cents: number) =>
    new Intl.NumberFormat(locale(language), { style: 'currency', currency: 'EUR', notation: cents >= 100_000 ? 'compact' : 'standard', maximumFractionDigits: 0 }).format(cents / 100);
  const dateLong = new Intl.DateTimeFormat(locale(language), { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
  const dateShort = new Intl.DateTimeFormat(locale(language), { day: 'numeric', month: 'numeric', timeZone: 'UTC' });
  const asDate = (iso: string) => new Date(`${iso}T00:00:00Z`);
  const range = `${dateLong.format(asDate(data.from))} – ${dateLong.format(asDate(data.to))}`;
  const daily = data.daily.map((d) => ({
    key: d.date,
    label: dateShort.format(asDate(d.date)),
    title: dateLong.format(asDate(d.date)),
    value: d.revenue.amountCents,
  }));
  const hourLabel = (h: number) => `${String(h).padStart(2, '0')}:00`;
  const hours = data.ordersByHour.map((count, h) => ({ key: String(h), label: String(h), title: `${hourLabel(h)}–${String(h).padStart(2, '0')}:59`, value: count }));
  const counted = data.totals.orders;

  return (
    <>
      <p className="muted small" style={{ margin: 0 }}>
        <bdi>{range}</bdi>
      </p>
      <div className="tiles">
        <Tile label={t('insights.revenue')} value={euro(data.totals.revenue.amountCents)} delta={change(data.totals.revenue.amountCents, data.previous.revenue.amountCents)} />
        <Tile label={t('insights.orders')} value={number.format(counted)} delta={change(counted, data.previous.orders)} />
        <Tile label={t('insights.average')} value={data.totals.averageOrderValue === null ? '–' : euro(data.totals.averageOrderValue.amountCents)} delta={averageChange(data.totals, data.previous)} />
        <Tile
          label={t('insights.customers')}
          value={number.format(data.customers)}
          note={data.customers > 0 ? t('insights.returning', { n: number.format(data.returningCustomers), percent: Math.round((data.returningCustomers / data.customers) * 100) }) : undefined}
        />
      </div>
      <p className="small muted" style={{ margin: 0 }}>
        {[
          t('insights.pickup', { count: data.pickupOrders, n: number.format(data.pickupOrders) }),
          t('insights.delivery', { count: data.deliveryOrders, n: number.format(data.deliveryOrders) }),
          t('insights.cancelled', { n: number.format(data.cancelledOrders) }),
          t('insights.rejected', { n: number.format(data.rejectedOrders) }),
          data.totals.discounts.amountCents > 0 ? t('insights.discounts', { amount: euro(data.totals.discounts.amountCents) }) : null,
        ]
          .filter((part) => part !== null)
          .join(' · ')}
      </p>

      <section className="card stack" aria-labelledby="insights-daily">
        <h2 id="insights-daily" style={{ margin: 0 }}>
          {t('insights.revenueByDay')}
        </h2>
        {counted === 0 ? <p className="muted">{t('insights.empty')}</p> : <ColumnChart columns={daily} format={compactEuro} label={t('insights.revenueByDay')} labelAnchor="end" minStep={100} />}
        <details>
          <summary className="small">{t('insights.asTable')}</summary>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">{t('insights.date')}</th>
                  <th scope="col">{t('insights.orders')}</th>
                  <th scope="col">{t('insights.revenue')}</th>
                </tr>
              </thead>
              <tbody>
                {data.daily.map((d) => (
                  <tr key={d.date}>
                    <th scope="row">{dateLong.format(asDate(d.date))}</th>
                    <td>
                      <bdi>{number.format(d.orders)}</bdi>
                    </td>
                    <td>
                      <bdi>{euro(d.revenue.amountCents)}</bdi>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </section>

      <div className="grid-2">
        <section className="card stack" aria-labelledby="insights-hours">
          <h2 id="insights-hours" style={{ margin: 0 }}>
            {t('insights.byHour')}
          </h2>
          <p className="muted small" style={{ margin: 0 }}>
            {t('insights.byHourHint')}
          </p>
          {counted === 0 ? <p className="muted">{t('insights.empty')}</p> : <ColumnChart columns={hours} format={(v) => number.format(v)} label={t('insights.byHour')} minStep={1} />}
          <details>
            <summary className="small">{t('insights.asTable')}</summary>
            <div className="table-wrap">
              <table>
                <tbody>
                  {data.ordersByHour.map((count, h) => (
                    <tr key={h}>
                      <th scope="row">
                        <bdi>{hours[h]?.title}</bdi>
                      </th>
                      <td>
                        <bdi>{number.format(count)}</bdi>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </section>

        <section className="card stack" aria-labelledby="insights-top">
          <h2 id="insights-top" style={{ margin: 0 }}>
            {t('insights.topProducts')}
          </h2>
          {data.topProducts.length === 0 ? (
            <p className="muted">{t('insights.empty')}</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">{t('insights.product')}</th>
                    <th scope="col">{t('insights.quantity')}</th>
                    <th scope="col">{t('insights.lineRevenue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.topProducts.map((p) => (
                    <tr key={p.listingId}>
                      <th scope="row" style={{ color: 'var(--text)', fontWeight: 500 }}>
                        {p.name}
                        {p.unitSize != null && (
                          <span className="muted small">
                            {' · '}
                            <bdi>{p.unitSize}</bdi>
                          </span>
                        )}
                      </th>
                      <td>
                        <bdi>{number.format(p.quantity)}</bdi>
                      </td>
                      <td>
                        <bdi>{euro(p.revenue.amountCents)}</bdi>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </>
  );
}

function averageChange(current: InsightTotals, previous: InsightTotals): number | null {
  return current.averageOrderValue === null || previous.averageOrderValue === null ? null : change(current.averageOrderValue.amountCents, previous.averageOrderValue.amountCents);
}

function Tile({ label, value, delta, note }: { label: string; value: string; delta?: number | null; note?: string | undefined }) {
  const { t } = useTranslation();
  return (
    <div className="tile">
      <span className="small muted">{label}</span>
      <strong className="tile-value">
        <bdi>{value}</bdi>
      </strong>
      {delta !== undefined && (
        <span className="small muted">
          {delta === null ? (
            t('insights.noComparison')
          ) : (
            <>
              <span aria-hidden="true">{delta > 0 ? '▲ ' : delta < 0 ? '▼ ' : '■ '}</span>
              {t(delta > 0 ? 'insights.up' : delta < 0 ? 'insights.down' : 'insights.same', { percent: Math.abs(delta) })}
            </>
          )}
        </span>
      )}
      {note !== undefined && <span className="small muted">{note}</span>}
    </div>
  );
}
