import { useQueries } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { MerchantMarket, Order } from '../api/types';
import { formatMoney } from '../ui/format';
import type { AlertSignals } from './signals';

export interface NewOrder {
  order: Order;
  market: MerchantMarket;
}

const MAX_TOASTS = 3;

/** First strong isolate … pop: keeps an order number or market name in order inside other text. */
const isolate = (text: string) => `⁨${text}⁩`;

/**
 * New order IDs of one market since its previous successful poll; its first poll only sets the
 * baseline. Orders never go back to PLACED, so the baseline is simply the current list.
 */
export function newArrivals(seen: Set<string> | undefined, current: string[]): { seen: Set<string>; added: string[] } {
  const next = new Set(current);
  return { seen: next, added: seen === undefined ? [] : current.filter((id) => !seen.has(id)) };
}

type Polled = { market: MerchantMarket; orders: Order[] | undefined }[];

interface Tracked {
  polled: Polled | null;
  baselines: Map<string, Set<string>>;
  arrivals: NewOrder[];
  batch: { id: number; orders: NewOrder[] } | null;
}

/** Compares a new poll result with the baselines of each market that has data. */
function track(previous: Tracked, polled: Polled): Tracked {
  const baselines = new Map(previous.baselines);
  const arrived: NewOrder[] = [];
  for (const { market, orders } of polled) {
    if (orders === undefined) {
      continue;
    }
    const next = newArrivals(baselines.get(market.id), orders.map((o) => o.id));
    baselines.set(market.id, next.seen);
    arrived.push(...orders.filter((o) => next.added.includes(o.id)).map((order) => ({ order, market })));
  }
  if (arrived.length === 0) {
    return { ...previous, polled, baselines };
  }
  return { polled, baselines, arrivals: [...arrived, ...previous.arrivals], batch: { id: (previous.batch?.id ?? 0) + 1, orders: arrived } };
}

/**
 * Watches every market of the merchant for new orders (also while another page of the portal is
 * open or the browser tab is in the background): the tab title counts orders waiting for a
 * decision, a toast names each new one, and with alerts switched on a chime plays and the system
 * shows a notification. Each market has its own baseline, so one market that fails to load does
 * not silence the others, and a market added later does not alert for its existing orders.
 */
export function OrderAlerts({
  markets,
  intervalMs,
  enabled,
  signals,
  onOpen,
}: {
  markets: MerchantMarket[];
  intervalMs: number;
  enabled: boolean;
  signals: AlertSignals;
  onOpen: (marketId: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const combine = useCallback(
    (results: { data?: Order[] | undefined }[]) => markets.map((market, i) => ({ market, orders: results[i]?.data })),
    [markets],
  );
  // Stable between renders while no poll changed anything (combine is memoised by TanStack Query).
  const polled = useQueries({
    queries: markets.map((market) => ({
      queryKey: ['placedOrders', market.id],
      queryFn: () => api.orders(market.id, 'PLACED'),
      refetchInterval: intervalMs,
      refetchIntervalInBackground: true,
    })),
    combine,
  });
  // Derived while rendering (React's "previous value" pattern): baselines per market, the open
  // arrivals, and the latest batch, which an effect then signals once.
  const [tracked, setTracked] = useState<Tracked>({ polled: null, baselines: new Map(), arrivals: [], batch: null });
  if (tracked.polled !== polled) {
    setTracked(track(tracked, polled));
  }
  const waiting = useMemo(() => polled.flatMap(({ market, orders }) => (orders ?? []).map((order) => ({ order, market }))), [polled]);

  const batch = tracked.batch;
  const signalled = useRef<number | null>(null);
  useEffect(() => {
    if (batch === null || signalled.current === batch.id || !enabled) {
      return;
    }
    signalled.current = batch.id;
    signals.sound();
    const first = batch.orders[0] as NewOrder;
    signals.notify(
      t('alerts.notificationTitle', { count: batch.orders.length }),
      [first.order.orderNumber, formatMoney(first.order.total, i18n.language), first.market.name].map(isolate).join(' · '),
    );
  }, [batch, enabled, signals, t, i18n.language]);
  const announcement =
    batch === null ? '' : t('alerts.announce', { count: batch.orders.length, numbers: batch.orders.map((a) => a.order.orderNumber).join(', ') });

  // The tab title shows how many orders wait for a decision.
  useEffect(() => {
    const base = t('app.title');
    document.title = waiting.length > 0 ? `(${waiting.length}) ${base}` : base;
    return () => {
      document.title = base;
    };
  }, [waiting.length, t]);

  // Accepted, rejected or cancelled orders drop out of the toasts by themselves.
  const arrivals = tracked.arrivals;
  const waitingIds = new Set(waiting.map((w) => w.order.id));
  const open = arrivals.filter((a) => waitingIds.has(a.order.id));
  const dismiss = (id: string) => setTracked((current) => ({ ...current, arrivals: current.arrivals.filter((a) => a.order.id !== id) }));

  return (
    <>
      {/* Always mounted, so screen readers announce the first new order too. */}
      <p className="visually-hidden" aria-live="polite" aria-atomic="true" data-testid="order-announcement">
        {announcement}
      </p>
      {open.length > 0 && (
        <div className="toasts">
          {open.slice(0, MAX_TOASTS).map(({ order, market }) => (
            <div key={order.id} className="toast">
              <div className="stack" style={{ gap: 2 }}>
                <strong>{t('alerts.newOrder')}</strong>
                <span className="small">
                  <bdi>{order.orderNumber}</bdi> · <bdi>{formatMoney(order.total, i18n.language)}</bdi>
                  {markets.length > 1 && (
                    <>
                      {' · '}
                      <bdi>{market.name}</bdi>
                    </>
                  )}
                </span>
              </div>
              <div className="row" style={{ gap: 6, flexWrap: 'nowrap' }}>
                <button
                  className="button"
                  type="button"
                  onClick={() => {
                    onOpen(market.id);
                    dismiss(order.id);
                  }}
                >
                  {t('alerts.open')}
                </button>
                <button className="button secondary" type="button" aria-label={t('alerts.dismiss', { number: order.orderNumber })} onClick={() => dismiss(order.id)}>
                  ×
                </button>
              </div>
            </div>
          ))}
          {open.length > MAX_TOASTS && (
            <div className="toast small">
              <span>{t('alerts.more', { count: open.length - MAX_TOASTS })}</span>
              <button
                className="button secondary"
                type="button"
                onClick={() => {
                  onOpen((open[MAX_TOASTS] as NewOrder).market.id);
                  setTracked((current) => ({ ...current, arrivals: [] }));
                }}
              >
                {t('alerts.open')}
              </button>
            </div>
          )}
        </div>
      )}
    </>
  );
}
