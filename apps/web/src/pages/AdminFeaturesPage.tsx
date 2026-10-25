import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { FeatureKey } from '../api/types';
import { errorMessage } from '../ui/errors';

const FEATURES: { key: FeatureKey; sub: boolean }[] = [
  { key: 'SHOPPING_LIST', sub: false },
  { key: 'SHOPPING_LIST_VOICE', sub: true },
  { key: 'SHOPPING_LIST_MEDIA', sub: true },
];

/**
 * Functions for all customers, switched by platform admins. Speaking and photo/video only reach
 * customers while the shopping list itself is on; the app hides whatever is off.
 */
export function AdminFeaturesPage() {
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const features = useQuery({ queryKey: ['adminFeatures'], queryFn: () => api.adminFeatures() });
  const toggle = useMutation({
    mutationFn: ({ key, enabled }: { key: FeatureKey; enabled: boolean }) => api.adminSetFeature(key, enabled),
    onSuccess: (updated) => queryClient.setQueryData(['adminFeatures'], updated),
  });
  const listOn = features.data?.SHOPPING_LIST === true;

  return (
    <section className="stack" aria-labelledby="features-title">
      <h1 id="features-title">{t('features.title')}</h1>
      <p className="muted small">{t('features.hint')}</p>
      {features.isPending && <p>{t('app.loading')}</p>}
      {(features.isError || toggle.isError) && (
        <p className="error" role="alert">
          {errorMessage(t, features.error ?? toggle.error)}
        </p>
      )}
      {features.data !== undefined && (
        <div className="card stack">
          <h2 style={{ margin: 0 }}>{t('features.shoppingList')}</h2>
          {FEATURES.map(({ key, sub }) => (
            <div key={key} className="stack tight" style={sub ? { marginInlineStart: 24 } : undefined}>
              <label className="chip" style={{ alignSelf: 'flex-start' }}>
                <input
                  type="checkbox"
                  checked={features.data[key]}
                  disabled={toggle.isPending}
                  onChange={(e) => toggle.mutate({ key, enabled: e.target.checked })}
                />
                {t(`features.name.${key}`)}
              </label>
              <span className="muted small">
                {t(`features.description.${key}`)}
                {sub && !listOn && ` ${t('features.needsList')}`}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
