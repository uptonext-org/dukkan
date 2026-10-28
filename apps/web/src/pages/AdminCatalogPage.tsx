import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { CatalogReviewItem, ReviewStatus } from '../api/types';
import { LANGUAGES } from '../i18n';
import { errorMessage } from '../ui/errors';
import { CatalogImportCard } from './CatalogImportCard';

const STATUSES: ReviewStatus[] = ['PENDING', 'KEPT_PRIVATE', 'PROMOTED'];

/** Review of products merchants create (B-36a): correct texts, promote to the shared catalog. */
export function AdminCatalogPage() {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const [status, setStatus] = useState<ReviewStatus>('PENDING');
  const queue = useQuery({
    queryKey: ['catalogReview', status, i18n.language],
    queryFn: () => api.catalogReviewQueue(status, 0),
    placeholderData: keepPreviousData,
  });

  return (
    <section className="stack" aria-labelledby="catalog-title">
      <h1 id="catalog-title">{t('catalog.title')}</h1>
      <p className="muted">{t('catalog.intro')}</p>
      <CatalogImportCard />
      <div className="row">
        <label className="field" style={{ maxWidth: 260 }}>
          {t('admin.filter')}
          <select value={status} onChange={(e) => setStatus(e.target.value as ReviewStatus)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`catalog.status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        {queue.data !== undefined && <span className="muted small">{t('catalog.count', { count: queue.data.totalItems })}</span>}
      </div>
      {queue.isPending && <p>{t('app.loading')}</p>}
      {queue.isError && (
        <p className="error" role="alert">
          {errorMessage(t, queue.error)}
        </p>
      )}
      {queue.data?.items.length === 0 && <p className="muted">{t('catalog.empty')}</p>}
      {queue.data?.items.map((item) => (
        <ReviewCard key={`${item.id}-${item.reviewStatus}-${JSON.stringify(item.texts)}`} item={item} />
      ))}
    </section>
  );
}

function ReviewCard({ item }: { item: CatalogReviewItem }) {
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [names, setNames] = useState<Record<string, string>>(() => Object.fromEntries(LANGUAGES.map((l) => [l.code, item.texts[l.code]?.name ?? ''])));
  const [descriptions, setDescriptions] = useState<Record<string, string>>(() =>
    Object.fromEntries(LANGUAGES.map((l) => [l.code, item.texts[l.code]?.description ?? ''])),
  );
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['catalogReview'] });
  const saveTexts = useMutation({
    mutationFn: () => {
      const changedNames: Record<string, string> = {};
      const changedDescriptions: Record<string, string> = {};
      for (const l of LANGUAGES) {
        const name = (names[l.code] ?? '').trim();
        const description = (descriptions[l.code] ?? '').trim();
        const current = item.texts[l.code];
        if (name !== '' && (name !== current?.name || description !== (current.description ?? ''))) {
          changedNames[l.code] = name;
          changedDescriptions[l.code] = description;
        }
      }
      return api.updateCatalogTexts(item.id, changedNames, changedDescriptions);
    },
    onSuccess: refresh,
  });
  const review = useMutation({ mutationFn: (decision: 'PROMOTE' | 'KEEP_PRIVATE') => api.reviewCatalogProduct(item.id, decision), onSuccess: refresh });
  const editable = item.reviewStatus !== 'PROMOTED';
  const dirty = LANGUAGES.some(
    (l) => (names[l.code] ?? '').trim() !== (item.texts[l.code]?.name ?? '') || (descriptions[l.code] ?? '').trim() !== (item.texts[l.code]?.description ?? ''),
  );
  const title = item.texts.de?.name ?? Object.values(item.texts)[0]?.name ?? item.id;

  return (
    <article className="card stack" aria-label={title}>
      <div className="row">
        <h2 style={{ margin: 0 }}>{title}</h2>
        <span className="badge">{t(`catalog.status.${item.reviewStatus}`)}</span>
      </div>
      <div className="muted small">
        {[item.market?.name, item.category?.name, item.brand, item.unitSize, item.barcode].filter((v) => v != null && v !== '').join(' · ')}
      </div>
      {item.duplicates.length > 0 && (
        <div className="small" role="note">
          <strong>{t('catalog.duplicates')}</strong>{' '}
          {item.duplicates.map((d) => `${d.name} (${d.unitSize}${d.barcode != null ? `, ${d.barcode}` : ''})`).join('; ')}
        </div>
      )}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">{t('app.language')}</th>
              <th scope="col">{t('catalog.name')}</th>
              <th scope="col">{t('catalog.description')}</th>
            </tr>
          </thead>
          <tbody>
            {LANGUAGES.map((l) => (
              <tr key={l.code}>
                <th scope="row">
                  {l.nativeName}
                  {item.texts[l.code]?.machineTranslated === true && <div className="badge PENDING_REVIEW small">{t('catalog.machine')}</div>}
                </th>
                <td>
                  <input
                    dir={l.direction}
                    lang={l.code}
                    aria-label={`${t('catalog.name')} · ${l.nativeName}`}
                    value={names[l.code] ?? ''}
                    disabled={!editable}
                    onChange={(e) => setNames({ ...names, [l.code]: e.target.value })}
                    style={{ width: '100%' }}
                  />
                </td>
                <td>
                  <input
                    dir={l.direction}
                    lang={l.code}
                    aria-label={`${t('catalog.description')} · ${l.nativeName}`}
                    value={descriptions[l.code] ?? ''}
                    disabled={!editable}
                    onChange={(e) => setDescriptions({ ...descriptions, [l.code]: e.target.value })}
                    style={{ width: '100%' }}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editable && (
        <div className="row">
          <button className="button secondary" type="button" disabled={!dirty || saveTexts.isPending} onClick={() => saveTexts.mutate()}>
            {t('catalog.saveTexts')}
          </button>
          <button className="button" type="button" disabled={dirty || review.isPending} onClick={() => review.mutate('PROMOTE')}>
            {t('catalog.promote')}
          </button>
          {item.reviewStatus === 'PENDING' && (
            <button className="button secondary" type="button" disabled={dirty || review.isPending} onClick={() => review.mutate('KEEP_PRIVATE')}>
              {t('catalog.keepPrivate')}
            </button>
          )}
        </div>
      )}
      {dirty && editable && <p className="muted small">{t('catalog.saveFirst')}</p>}
      {(saveTexts.isError || review.isError) && (
        <p className="error" role="alert">
          {errorMessage(t, saveTexts.error ?? review.error)}
        </p>
      )}
    </article>
  );
}
