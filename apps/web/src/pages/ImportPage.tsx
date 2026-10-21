import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { ImportResult } from '../api/types';
import { errorMessage } from '../ui/errors';

/** Semicolons and a BOM, so Excel in German opens it with the right columns and umlauts. */
export const CSV_TEMPLATE =
  '﻿' +
  [
    'barcode;name_de;name_en;name_ar;description_de;brand;unit_size;category;price;available;max_order_quantity;keywords;cuisines;allergens;organic',
    '8690000000001;Sucuk;Sucuk beef sausage;سجق;Würzige Rinderwurst;Gazi;400 g;meat;4,49;ja;10;sucuk|sujuk;TURKISH;mustard;nein',
    ';Simit;Simit;سميت;Sesamring, jeden Morgen frisch;;1 Stück;bakery;0,80;ja;;;TURKISH;gluten|sesame;nein',
  ].join('\r\n') +
  '\r\n';

function downloadTemplate() {
  const url = URL.createObjectURL(new Blob([CSV_TEMPLATE], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'dukkan-produkte-vorlage.csv';
  link.click();
  URL.revokeObjectURL(url);
}

export function ImportPage({ marketId }: { marketId: string }) {
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportResult | null>(null);
  const run = useMutation({
    mutationFn: ({ dryRun }: { dryRun: boolean }) => api.importListings(marketId, file as File, dryRun),
    onSuccess: (result) => {
      setReport(result);
      if (result.applied) {
        void queryClient.invalidateQueries({ queryKey: ['listings', marketId] });
      }
    },
  });
  const ready = report !== null && report.dryRun && report.failed === 0 && report.rows.length > 0;

  return (
    <section className="stack" aria-labelledby="import-title">
      <h1 id="import-title">{t('import.title')}</h1>
      <p>{t('import.intro')}</p>
      <p className="muted small">{t('import.columns')}</p>
      <div className="row">
        <button className="button secondary" type="button" onClick={downloadTemplate}>
          {t('import.template')}
        </button>
      </div>
      <div className="card row">
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
          {run.isPending && run.variables.dryRun ? t('import.checking') : t('import.check')}
        </button>
        {ready && (
          <button className="button" type="button" disabled={run.isPending} onClick={() => run.mutate({ dryRun: false })}>
            {run.isPending && !run.variables.dryRun ? t('import.applying') : t('import.apply', { count: report.rows.length })}
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
            <strong>{t('import.summary', { created: report.created, listed: report.listed, updated: report.updated, failed: report.failed })}</strong>
          </p>
          {report.failed > 0 && (
            <p className="error" role="alert">
              {t('import.fixErrors')}
            </p>
          )}
          {report.applied && (
            <p className="success" role="status">
              {t('import.done', { count: report.rows.length })}
            </p>
          )}
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">{t('import.line')}</th>
                  <th scope="col">{t('import.action')}</th>
                  <th scope="col">{t('import.product')}</th>
                  <th scope="col">{t('import.problems')}</th>
                </tr>
              </thead>
              <tbody>
                {report.rows.map((row) => (
                  <tr key={row.line} className={row.action}>
                    <td>
                      <bdi>{row.line}</bdi>
                    </td>
                    <td>{t(`import.actions.${row.action}`)}</td>
                    <td>
                      <bdi>{row.name ?? '—'}</bdi>
                    </td>
                    <td>
                      {row.errors.map((e) => (
                        <div key={`${e.field}-${e.code}`} className="error small">
                          <code dir="ltr">{e.field}</code>: <span dir="ltr">{e.message}</span>
                        </div>
                      ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
