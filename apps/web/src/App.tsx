import { keepPreviousData, QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useEffect, useState, useSyncExternalStore } from 'react';
import { useTranslation } from 'react-i18next';

import { OrderAlerts } from './alerts/OrderAlerts';
import { browserSignals, loadAlertsEnabled, saveAlertsEnabled, type AlertSignals } from './alerts/signals';
import { ApiProvider, useApi } from './api/ApiContext';
import { createApi, type Session, type SessionStore } from './api/client';
import { applyLanguage, isLanguage, LANGUAGES } from './i18n';
import { AdminCatalogPage } from './pages/AdminCatalogPage';
import { AdminFeaturesPage } from './pages/AdminFeaturesPage';
import { AdminMarketsPage } from './pages/AdminMarketsPage';
import { ImportPage } from './pages/ImportPage';
import { InsightsPage } from './pages/InsightsPage';
import { ListingsPage } from './pages/ListingsPage';
import { LoginPage } from './pages/LoginPage';
import { MarketProfilePage } from './pages/MarketProfilePage';
import { OrderSettingsPage } from './pages/OrderSettingsPage';
import { OrdersBoard } from './pages/OrdersBoard';
import { PromotionsPage } from './pages/PromotionsPage';
import { errorMessage } from './ui/errors';

type Route = 'orders' | 'insights' | 'listings' | 'promotions' | 'import' | 'profile' | 'settings' | 'admin' | 'catalog' | 'features';
const ROUTES: Route[] = ['orders', 'insights', 'listings', 'promotions', 'import', 'profile', 'settings', 'admin', 'catalog', 'features'];

function subscribeHash(onChange: () => void) {
  window.addEventListener('hashchange', onChange);
  return () => window.removeEventListener('hashchange', onChange);
}

function useHashRoute(): [Route | null, (route: Route) => void] {
  const hash = useSyncExternalStore(subscribeHash, () => window.location.hash);
  const route = ROUTES.find((r) => hash === `#/${r}`) ?? null;
  return [route, (next) => (window.location.hash = `#/${next}`)];
}

export interface AppDependencies {
  fetch?: typeof fetch;
  store?: SessionStore;
  queryClient?: QueryClient;
  /** How often new orders are looked for (default 20 s), and how merchants are alerted. */
  alerts?: { intervalMs?: number; signals?: AlertSignals; enabled?: boolean };
}

export function App({ dependencies = {} }: { dependencies?: AppDependencies }) {
  const { i18n } = useTranslation();
  const [{ api, queryClient, subscribe }] = useState(() => {
    const listeners = new Set<() => void>();
    const client = dependencies.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true } } });
    const created = createApi({
      ...(dependencies.fetch !== undefined ? { fetch: dependencies.fetch } : {}),
      ...(dependencies.store !== undefined ? { store: dependencies.store } : {}),
      language: () => i18n.language,
      onSessionChange: (next) => {
        if (next === null) {
          // Signed out (or the session expired): no data of the previous account stays cached.
          client.clear();
        }
        listeners.forEach((listener) => listener());
      },
    });
    return {
      api: created,
      queryClient: client,
      subscribe: (listener: () => void) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
      },
    };
  });
  const session = useSyncExternalStore(subscribe, () => api.session());

  return (
    <QueryClientProvider client={queryClient}>
      <ApiProvider value={{ api, session }}>{session === null ? <LoginPage /> : <Shell session={session} alerts={dependencies.alerts} />}</ApiProvider>
    </QueryClientProvider>
  );
}

function Shell({ session, alerts }: { session: Session; alerts: AppDependencies['alerts'] }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const isMerchant = session.user.roles.includes('MERCHANT');
  const isAdmin = session.user.roles.includes('ADMIN');
  const [route, setRoute] = useHashRoute();
  const markets = useQuery({ queryKey: ['myMarkets', i18n.language], queryFn: () => api.myMarkets(), enabled: isMerchant, placeholderData: keepPreviousData });
  const [marketId, setMarketId] = useState<string | null>(null);
  const selected = markets.data?.find((m) => m.id === marketId) ?? markets.data?.[0];
  const signals = alerts?.signals ?? browserSignals;
  const [alertsOn, setAlertsOn] = useState(() => alerts?.enabled ?? loadAlertsEnabled());
  const [notificationsBlocked, setNotificationsBlocked] = useState(false);
  // Switched on in an earlier visit: audio needs a gesture in this one, so the first click or key unlocks it.
  useEffect(() => {
    if (!alertsOn) {
      return;
    }
    const unlock = () => {
      signals.prepare();
    };
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [alertsOn, signals]);

  const available: Route[] = [...(isMerchant ? (['orders', 'insights', 'listings', 'promotions', 'import', 'profile', 'settings'] as const) : []), ...(isAdmin ? (['admin', 'catalog', 'features'] as const) : [])];
  const current = route !== null && available.includes(route) ? route : available[0];

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          {t('app.title')}
          <small>
            {session.user.firstName} {session.user.lastName}
          </small>
        </div>
        {isMerchant && markets.data !== undefined && markets.data.length > 1 && (
          <label className="field">
            {t('nav.market')}
            <select value={selected?.id ?? ''} onChange={(e) => setMarketId(e.target.value)}>
              {markets.data.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {selected !== undefined && <span className={`badge ${selected.status}`}>{`${selected.name} · ${t(`status.${selected.status}`)}`}</span>}
        <label className="field">
          {t('app.language')}
          <select value={i18n.language} onChange={(e) => isLanguage(e.target.value) && applyLanguage(e.target.value)}>
            {LANGUAGES.map((l) => (
              <option key={l.code} value={l.code} lang={l.code}>
                {l.nativeName}
              </option>
            ))}
          </select>
        </label>
        {isMerchant && (
          <button
            className="button secondary"
            type="button"
            aria-pressed={alertsOn}
            title={notificationsBlocked && alertsOn ? t('alerts.blocked') : t('alerts.toggleHint')}
            onClick={() => {
              const next = !alertsOn;
              if (next) {
                // From the click: browsers allow sound and the permission prompt only after a gesture.
                setNotificationsBlocked(!signals.prepare());
              }
              saveAlertsEnabled(next);
              setAlertsOn(next);
            }}
          >
            <span aria-hidden="true">{alertsOn ? '🔔 ' : '🔕 '}</span>
            {t('alerts.label')}
          </button>
        )}
        <button className="button secondary" type="button" onClick={() => void api.logout()}>
          {t('app.logout')}
        </button>
      </header>
      <nav className="tabs" aria-label={t('app.title')}>
        {available.map((r) => (
          <button key={r} type="button" className="tab" aria-current={r === current ? 'page' : undefined} onClick={() => setRoute(r)}>
            {t(`nav.${r}`)}
          </button>
        ))}
      </nav>
      {isMerchant && markets.data !== undefined && markets.data.length > 0 && (
        <OrderAlerts
          markets={markets.data}
          intervalMs={alerts?.intervalMs ?? 20_000}
          enabled={alertsOn}
          signals={signals}
          onOpen={(id) => {
            setMarketId(id);
            setRoute('orders');
          }}
        />
      )}
      <main>
        {current === 'admin' && <AdminMarketsPage />}
        {current === 'catalog' && <AdminCatalogPage />}
        {current === 'features' && <AdminFeaturesPage />}
        {current !== 'admin' && current !== 'catalog' && current !== 'features' && current !== undefined && (
          <>
            {markets.isPending && <p>{t('app.loading')}</p>}
            {markets.isError && (
              <p className="error" role="alert">
                {errorMessage(t, markets.error)}
              </p>
            )}
            {selected !== undefined && current === 'orders' && <OrdersBoard marketId={selected.id} onOpenSettings={() => setRoute('settings')} />}
            {selected !== undefined && current === 'insights' && <InsightsPage key={selected.id} marketId={selected.id} />}
            {selected !== undefined && current === 'listings' && <ListingsPage marketId={selected.id} />}
            {selected !== undefined && current === 'import' && <ImportPage marketId={selected.id} />}
            {selected !== undefined && current === 'profile' && <MarketProfilePage market={selected} />}
            {selected !== undefined && current === 'settings' && <OrderSettingsPage marketId={selected.id} />}
            {selected !== undefined && current === 'promotions' && <PromotionsPage marketId={selected.id} primaryLocale={selected.primaryLocale} />}
          </>
        )}
      </main>
    </div>
  );
}

