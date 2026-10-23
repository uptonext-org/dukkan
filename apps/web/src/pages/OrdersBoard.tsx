import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { Order, OrderItem, OrderStatus } from '../api/types';
import { errorMessage } from '../ui/errors';
import { formatMoney, formatSlot } from '../ui/format';

const COLUMNS: { key: 'new' | 'accepted' | 'ready' | 'done'; statuses: OrderStatus[] }[] = [
  { key: 'new', statuses: ['PLACED'] },
  { key: 'accepted', statuses: ['ACCEPTED'] },
  { key: 'ready', statuses: ['READY_FOR_PICKUP', 'OUT_FOR_DELIVERY'] },
  { key: 'done', statuses: ['COMPLETED', 'REJECTED', 'CANCELLED'] },
];

/** Finished orders shown per column; older ones stay in the app's order history. */
const DONE_LIMIT = 20;

/** The next statuses a merchant may set (the server's state machine, see advanceOrderStatus). */
export function nextStatuses(order: Order): OrderStatus[] {
  switch (order.status) {
    case 'PLACED':
      return ['ACCEPTED', 'REJECTED'];
    case 'ACCEPTED':
      return [order.fulfilmentType === 'PICKUP' ? 'READY_FOR_PICKUP' : 'OUT_FOR_DELIVERY'];
    case 'READY_FOR_PICKUP':
    case 'OUT_FOR_DELIVERY':
      return ['COMPLETED'];
    default:
      return [];
  }
}

/** Units handed over (the ordered quantity unless the market entered fewer). */
export function handedOver(item: OrderItem): number {
  return item.fulfilledQuantity ?? item.quantity;
}

/**
 * Missing items while packing: one number per line (0 = missing), saved together. Only lines that
 * differ from what is stored are sent.
 */
function MissingItemsEditor({ order, onDone }: { order: Order; onDone: () => void }) {
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [units, setUnits] = useState<Record<number, number>>(() => Object.fromEntries(order.items.map((i) => [i.lineNo, handedOver(i)])));
  const save = useMutation({
    mutationFn: () =>
      api.adjustOrderItems(
        order.id,
        order.items.filter((i) => units[i.lineNo] !== handedOver(i)).map((i) => ({ lineNo: i.lineNo, fulfilledQuantity: units[i.lineNo] ?? handedOver(i) })),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      onDone();
    },
  });
  const changed = order.items.some((i) => units[i.lineNo] !== handedOver(i));
  return (
    <form
      className="stack small"
      onSubmit={(event) => {
        event.preventDefault();
        save.mutate();
      }}>
      <p className="muted">{t('orders.adjust.missingHint')}</p>
      {order.items.map((item) => (
        <label key={item.lineNo} className="row">
          <span>
            <bdi>{item.quantity}×</bdi> {item.name}
          </span>
          <input
            type="number"
            min={0}
            max={item.quantity}
            value={units[item.lineNo] ?? handedOver(item)}
            aria-label={t('orders.adjust.handedOver', { name: item.name })}
            onChange={(event) => {
              const value = Math.max(0, Math.min(item.quantity, Number.parseInt(event.target.value, 10) || 0));
              setUnits((current) => ({ ...current, [item.lineNo]: value }));
            }}
            style={{ inlineSize: 64 }}
          />
        </label>
      ))}
      {save.isError && (
        <p className="error" role="alert">
          {errorMessage(t, save.error)}
        </p>
      )}
      <div className="row">
        <button type="submit" className="button" disabled={!changed || save.isPending}>
          {t('orders.adjust.save')}
        </button>
        <button type="button" className="button secondary" onClick={onDone}>
          {t('orders.adjust.cancel')}
        </button>
      </div>
    </form>
  );
}

export function OrdersBoard({ marketId }: { marketId: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const orders = useQuery({ queryKey: ['orders', marketId, i18n.language], queryFn: () => api.orders(marketId), refetchInterval: 30_000, placeholderData: keepPreviousData });
  const [editing, setEditing] = useState<string | null>(null);
  const advance = useMutation({
    mutationFn: ({ order, status }: { order: Order; status: OrderStatus }) => api.advanceOrder(order.id, status),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', marketId] });
      // The tab title counts orders waiting for a decision.
      void queryClient.invalidateQueries({ queryKey: ['placedOrders', marketId] });
    },
  });

  return (
    <section className="stack" aria-labelledby="orders-title">
      <div className="row">
        <h1 id="orders-title">{t('orders.title')}</h1>
        <button className="button secondary" type="button" onClick={() => void orders.refetch()}>
          {t('orders.refresh')}
        </button>
        <span className="muted small">{t('orders.autoRefresh')}</span>
      </div>
      {orders.isPending && <p>{t('app.loading')}</p>}
      {orders.isError && (
        <p className="error" role="alert">
          {errorMessage(t, orders.error)}
        </p>
      )}
      {advance.isError && (
        <p className="error" role="alert">
          {errorMessage(t, advance.error)}
        </p>
      )}
      {orders.data !== undefined && (
        <div className="board">
          {COLUMNS.map((column) => {
            const all = orders.data.filter((o) => column.statuses.includes(o.status));
            const shown = column.key === 'done' ? all.slice(0, DONE_LIMIT) : all;
            return (
              <section key={column.key} className="column" aria-labelledby={`column-${column.key}`}>
                <h2 id={`column-${column.key}`}>
                  <span>{t(`orders.column.${column.key}`)}</span>
                  <span className="badge">{all.length}</span>
                </h2>
                {shown.length === 0 && <p className="muted small">{t('orders.empty')}</p>}
                {shown.map((order) => (
                  <article key={order.id} className="order" aria-label={order.orderNumber}>
                    <div className="row">
                      <strong>
                        <bdi>{order.orderNumber}</bdi>
                      </strong>
                      <span className="badge">{t(`orders.state.${order.status}`)}</span>
                    </div>
                    <div className="small muted">
                      {order.fulfilmentType === 'PICKUP' ? t('orders.pickup') : t('orders.delivery')} · <bdi>{formatSlot(order.slot.startsAt, order.slot.endsAt, i18n.language)}</bdi>
                    </div>
                    {order.customerName != null && <div className="small">{order.customerName}</div>}
                    {editing === order.id ? (
                      <MissingItemsEditor order={order} onDone={() => setEditing(null)} />
                    ) : (
                      <ul className="small">
                        {order.items.map((item) => (
                          <li key={item.lineNo}>
                            {item.fulfilledQuantity != null ? (
                              <>
                                <bdi>{t('orders.adjust.of', { fulfilled: item.fulfilledQuantity, quantity: item.quantity })}</bdi> ·{' '}
                                {item.fulfilledQuantity === 0 ? <s>{item.name}</s> : item.name}
                              </>
                            ) : (
                              <>
                                <bdi>{item.quantity}×</bdi> {item.name}
                              </>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                    <div className="row">
                      <strong>
                        <bdi>{formatMoney(order.total, i18n.language)}</bdi>
                      </strong>
                      {order.originalTotal != null && (
                        <span className="muted small">
                          <s>
                            <bdi>{t('orders.adjust.instead', { amount: formatMoney(order.originalTotal, i18n.language) })}</bdi>
                          </s>
                        </span>
                      )}
                      <span className="muted small">{t('orders.items', { count: order.items.reduce((n, i) => n + handedOver(i), 0) })}</span>
                    </div>
                    <div className="row" style={{ marginBlockStart: 8 }}>
                      {(order.status === 'PLACED' || order.status === 'ACCEPTED') && editing !== order.id && (
                        <button type="button" className="button secondary" onClick={() => setEditing(order.id)}>
                          {t('orders.adjust.missing')}
                        </button>
                      )}
                      {nextStatuses(order).map((status) => (
                        <button
                          key={status}
                          type="button"
                          className={status === 'REJECTED' ? 'button danger' : 'button'}
                          disabled={advance.isPending}
                          onClick={() => advance.mutate({ order, status })}>
                          {t(`orders.action.${status}`)}
                        </button>
                      ))}
                    </div>
                  </article>
                ))}
              </section>
            );
          })}
        </div>
      )}
    </section>
  );
}
