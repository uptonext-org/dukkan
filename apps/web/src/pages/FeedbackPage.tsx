import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import { errorMessage } from '../ui/errors';
import { formatDateTime } from '../ui/format';

/** Customer feedback to the market: switch receiving it on or off and read the messages. */
export function FeedbackPage({ marketId }: { marketId: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const feedback = useInfiniteQuery({
    queryKey: ['feedback', marketId],
    queryFn: ({ pageParam }) => api.merchantFeedback(marketId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.feedback.page + 1 < last.feedback.totalPages ? last.feedback.page + 1 : undefined),
  });
  const toggle = useMutation({
    mutationFn: (enabled: boolean) => api.setFeedbackEnabled(marketId, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['feedback', marketId] }),
  });
  const first = feedback.data?.pages[0];
  const items = feedback.data?.pages.flatMap((p) => p.feedback.items) ?? [];

  return (
    <section className="stack" aria-labelledby="feedback-title">
      <h1 id="feedback-title">{t('feedback.title')}</h1>
      {feedback.isPending && <p>{t('app.loading')}</p>}
      {(feedback.isError || toggle.isError) && (
        <p className="error" role="alert">
          {errorMessage(t, feedback.error ?? toggle.error)}
        </p>
      )}
      {first !== undefined && (
        <div className="card stack tight">
          <label className="chip" style={{ alignSelf: 'flex-start' }}>
            <input type="checkbox" checked={toggle.isPending ? toggle.variables : first.enabled} disabled={toggle.isPending} onChange={(e) => toggle.mutate(e.target.checked)} />
            {t('feedback.toggle')}
          </label>
          <span className="muted small">{t('feedback.toggleHint')}</span>
        </div>
      )}
      {first !== undefined && items.length === 0 && <p className="muted">{t('feedback.empty')}</p>}
      {items.length > 0 && (
        <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {items.map((f) => (
            <li key={f.id} className="card stack tight">
              <span className="muted small">
                {[f.customerName, formatDateTime(f.createdAt, i18n.language), f.rating != null ? t('feedback.rating', { rating: f.rating }) : null].filter(Boolean).join(' · ')}
              </span>
              <p style={{ margin: 0, whiteSpace: 'pre-wrap' }} dir="auto">
                {f.message}
              </p>
            </li>
          ))}
        </ul>
      )}
      {feedback.hasNextPage && (
        <button className="button secondary" type="button" disabled={feedback.isFetchingNextPage} onClick={() => void feedback.fetchNextPage()}>
          {t('feedback.more')}
        </button>
      )}
    </section>
  );
}
