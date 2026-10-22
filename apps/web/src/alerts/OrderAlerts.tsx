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

/** IDs of orders that appeared since the previous poll; the first poll only sets the baseline. */
export function newArrivals(seen: Set<string> | null, current: string[]): { seen: Set<string>; added: string[] } {
  if (seen === null) {
    return { seen: new Set(current), added: [] };
  }
  const added = current.filter((id) => !seen.has(id));
  return { seen: added.length === 0 ? seen : new Set([...seen, ...added]), added };
}

/**
 * Watches every market of the merchant for new orders (also while another tab of the portal is
 * open or the browser tab is in the background): the tab title counts orders waiting for a
 * decision, a toast names each new one, and with alerts switched on a chime plays and the system
 * shows a notification.
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
    (results: { data?: Order[] | undefined }[]) => {
      if (results.length === 0 || results.some((r) => r.data === undefined)) {
        return null;
      }
      return results.flatMap((r, i) => (r.data as Order[]).map((order) => ({ order, market: markets[i] as MerchantMarket })));
    },
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
  const seen = useRef<Set<string> | null>(null);
  const [toasts, setToasts] = useState<NewOrder[]>([]);
  const ready = polled !== null;
  const waiting = useMemo(() => polled ?? [], [polled]);

  // Idempotent: the seen set makes a run without new orders a no-op.
  useEffect(() => {
    if (!ready) {
      return;
    }
    const next = newArrivals(seen.current, waiting.map((w) => w.order.id));
    seen.current = next.seen;
    if (next.added.length === 0) {
      return;
    }
    const arrived = waiting.filter((w) => next.added.includes(w.order.id));
    setToasts((current) => [...arrived, ...current].slice(0, MAX_TOASTS));
    if (enabled) {
      signals.sound();
      const first = arrived[0];
      if (first !== undefined) {
        signals.notify(
          t('alerts.notificationTitle', { count: arrived.length }),
          `${first.order.orderNumber} · ${formatMoney(first.order.total, i18n.language)} · ${first.market.name}`,
        );
      }
    }
  }, [ready, waiting, enabled, signals, t, i18n.language]);

  // The tab title shows how many orders wait for a decision.
  useEffect(() => {
    const base = t('app.title');
    document.title = waiting.length > 0 ? `(${waiting.length}) ${base}` : base;
    return () => {
      document.title = base;
    };
  }, [waiting.length, t]);

  if (toasts.length === 0) {
    return null;
  }
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map(({ order, market }) => (
        <div key={order.id} className="toast">
          <div className="stack" style={{ gap: 2 }}>
            <strong>{t('alerts.newOrder')}</strong>
            <span className="small">
              <bdi>{order.orderNumber}</bdi> · <bdi>{formatMoney(order.total, i18n.language)}</bdi>
              {markets.length > 1 && <> · {market.name}</>}
            </span>
          </div>
          <div className="row" style={{ gap: 6, flexWrap: 'nowrap' }}>
            <button
              className="button"
              type="button"
              onClick={() => {
                onOpen(market.id);
                setToasts((current) => current.filter((x) => x.order.id !== order.id));
              }}
            >
              {t('alerts.open')}
            </button>
            <button
              className="button secondary"
              type="button"
              aria-label={t('alerts.dismiss', { number: order.orderNumber })}
              onClick={() => setToasts((current) => current.filter((x) => x.order.id !== order.id))}
            >
              ×
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
