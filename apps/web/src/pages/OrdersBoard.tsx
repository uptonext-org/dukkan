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
 * Missing items while packing: one number per line (0 = missing), saved together. Only the lines
 * the merchant changed are sent, so a newer change by someone else to another line (the board
 * refreshes every 30 s) is not overwritten.
 */
function MissingItemsEditor({ order, onDone }: { order: Order; onDone: () => void }) {
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  // Typed text per touched line; untouched lines follow the order as it is now.
  const [edits, setEdits] = useState<Record<number, string>>({});
  const parsed = order.items.flatMap((item) => {
    const raw = edits[item.lineNo];
    if (raw === undefined || raw.trim() === '') {
      return [];
    }
    const value = Number(raw);
    return [{ item, value, valid: Number.isInteger(value) && value >= 0 && value <= item.quantity }];
  });
  const changes = parsed.filter((p) => p.valid && p.value !== handedOver(p.item));
  const invalid = parsed.some((p) => !p.valid);
  const save = useMutation({
    mutationFn: () => api.adjustOrderItems(order.id, changes.map((c) => ({ lineNo: c.item.lineNo, fulfilledQuantity: c.value }))),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      onDone();
    },
  });
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
            value={edits[item.lineNo] ?? String(handedOver(item))}
            aria-label={t('orders.adjust.handedOver', { name: item.name })}
            onChange={(event) => {
              const raw = event.target.value;
              setEdits((current) => ({ ...current, [item.lineNo]: raw }));
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
        <button type="submit" className="button" disabled={changes.length === 0 || invalid || save.isPending}>
          {t('orders.adjust.save')}
        </button>
        <button type="button" className="button secondary" onClick={onDone}>
          {t('orders.adjust.cancel')}
        </button>
      </div>
    </form>
  );
}

/** Missing items can be entered while the market packs (the server's rule). */
function isAdjustable(order: Order): boolean {
  return order.status === 'PLACED' || order.status === 'ACCEPTED';
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
                    {editing === order.id && isAdjustable(order) ? (
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
                      {isAdjustable(order) && editing !== order.id && (
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
