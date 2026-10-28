import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { CatalogImportResult } from '../api/types';
import { errorMessage } from '../ui/errors';

/**
 * Admins fill the shared catalog from a CSV file (e.g. the start catalog `start-katalog.csv`):
 * check first, then import. Products already in the catalog are skipped.
 */
export function CatalogImportCard() {
  const { t } = useTranslation();
  const api = useApi();
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<CatalogImportResult | null>(null);
  const run = useMutation({
    mutationFn: ({ dryRun }: { dryRun: boolean }) => api.importCatalog(file as File, dryRun),
    onSuccess: setReport,
  });
  const ready = report !== null && report.dryRun && report.failed === 0 && report.created > 0;
  const problems = report?.rows.filter((r) => r.action === 'ERROR') ?? [];

  return (
    <details className="card">
      <summary>{t('catalogImport.title')}</summary>
      <div className="stack">
        <p className="muted small">{t('catalogImport.intro')}</p>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            {t('import.file')}
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null);
                setReport(null);
                run.reset();
              }}
            />
          </label>
          <button className="button" type="button" disabled={file === null || run.isPending} onClick={() => run.mutate({ dryRun: true })}>
            {t('import.check')}
          </button>
          {ready && (
            <button className="button" type="button" disabled={run.isPending} onClick={() => run.mutate({ dryRun: false })}>
              {t('catalogImport.apply', { count: report.created })}
            </button>
          )}
        </div>
        {run.isError && (
          <p className="error" role="alert">
            {errorMessage(t, run.error)}
          </p>
        )}
        {report !== null && (
          <div className="stack" aria-live="polite">
            <p>
              <strong>{t('catalogImport.summary', { created: report.created, existing: report.existing, failed: report.failed })}</strong>
            </p>
            {report.applied && (
              <p className="success" role="status">
                {t('catalogImport.done', { count: report.created })}
              </p>
            )}
            {problems.length > 0 && (
              <ul className="stack tight" style={{ margin: 0 }}>
                {problems.map((row) => (
                  <li key={row.line} className="error small">
                    {t('import.line')} <bdi>{row.line}</bdi> (<bdi>{row.name ?? '—'}</bdi>):{' '}
                    {row.errors.map((e) => (
                      <span key={`${e.field}-${e.code}`} dir="ltr">
                        <code>{e.field}</code> {e.message}.{' '}
                      </span>
                    ))}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </details>
  );
}
