import { keepPreviousData, QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useState, useSyncExternalStore } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiProvider, useApi } from './api/ApiContext';
import { createApi, type Session, type SessionStore } from './api/client';
import { applyLanguage, isLanguage, LANGUAGES } from './i18n';
import { AdminCatalogPage } from './pages/AdminCatalogPage';
import { AdminMarketsPage } from './pages/AdminMarketsPage';
import { ImportPage } from './pages/ImportPage';
import { InsightsPage } from './pages/InsightsPage';
import { ListingsPage } from './pages/ListingsPage';
import { LoginPage } from './pages/LoginPage';
import { OrdersBoard } from './pages/OrdersBoard';
import { PromotionsPage } from './pages/PromotionsPage';
import { errorMessage } from './ui/errors';

type Route = 'orders' | 'insights' | 'listings' | 'promotions' | 'import' | 'admin' | 'catalog';
const ROUTES: Route[] = ['orders', 'insights', 'listings', 'promotions', 'import', 'admin', 'catalog'];

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
      <ApiProvider value={{ api, session }}>{session === null ? <LoginPage /> : <Shell session={session} />}</ApiProvider>
    </QueryClientProvider>
  );
}

function Shell({ session }: { session: Session }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const isMerchant = session.user.roles.includes('MERCHANT');
  const isAdmin = session.user.roles.includes('ADMIN');
  const [route, setRoute] = useHashRoute();
  const markets = useQuery({ queryKey: ['myMarkets', i18n.language], queryFn: () => api.myMarkets(), enabled: isMerchant, placeholderData: keepPreviousData });
  const [marketId, setMarketId] = useState<string | null>(null);
  const selected = markets.data?.find((m) => m.id === marketId) ?? markets.data?.[0];

  const available: Route[] = [...(isMerchant ? (['orders', 'insights', 'listings', 'promotions', 'import'] as const) : []), ...(isAdmin ? (['admin', 'catalog'] as const) : [])];
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
      <main>
        {current === 'admin' && <AdminMarketsPage />}
        {current === 'catalog' && <AdminCatalogPage />}
        {current !== 'admin' && current !== 'catalog' && current !== undefined && (
          <>
            {markets.isPending && <p>{t('app.loading')}</p>}
            {markets.isError && (
              <p className="error" role="alert">
                {errorMessage(t, markets.error)}
              </p>
            )}
            {selected !== undefined && current === 'orders' && <OrdersBoard marketId={selected.id} />}
            {selected !== undefined && current === 'insights' && <InsightsPage marketId={selected.id} />}
            {selected !== undefined && current === 'listings' && <ListingsPage marketId={selected.id} />}
            {selected !== undefined && current === 'import' && <ImportPage marketId={selected.id} />}
            {selected !== undefined && current === 'promotions' && <PromotionsPage marketId={selected.id} primaryLocale={selected.primaryLocale} />}
          </>
        )}
      </main>
    </div>
  );
}

