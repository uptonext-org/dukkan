import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { Order, OrderStatus } from '../api/types';
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

export function OrdersBoard({ marketId }: { marketId: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const orders = useQuery({ queryKey: ['orders', marketId, i18n.language], queryFn: () => api.orders(marketId), refetchInterval: 30_000, placeholderData: keepPreviousData });
  const advance = useMutation({
    mutationFn: ({ order, status }: { order: Order; status: OrderStatus }) => api.advanceOrder(order.id, status),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', marketId] }),
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
                    <ul className="small">
                      {order.items.map((item) => (
                        <li key={item.productId}>
                          <bdi>{item.quantity}×</bdi> {item.name}
                        </li>
                      ))}
                    </ul>
                    <div className="row">
                      <strong>
                        <bdi>{formatMoney(order.total, i18n.language)}</bdi>
                      </strong>
                      <span className="muted small">{t('orders.items', { count: order.items.reduce((n, i) => n + i.quantity, 0) })}</span>
                    </div>
                    <div className="row" style={{ marginBlockStart: 8 }}>
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
