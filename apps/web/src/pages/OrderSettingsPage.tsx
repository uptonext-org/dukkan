import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import type { MerchantOrderSettings, OrderSettings } from '../api/types';
import { errorMessage } from '../ui/errors';
import { HoursEditor } from './MarketProfilePage';
import { endOfDay, fromSettings, PAYMENT_METHODS, toSettings, withPause, type SettingsForm, type SettingsProblems } from './orderSettings';

/**
 * "Orders & delivery": everything a merchant controls about ordering. Each section says what
 * customers see of it; settings that are off or empty do not appear on the market page at all.
 */
export function OrderSettingsPage({ marketId }: { marketId: string }) {
  const { t } = useTranslation();
  const api = useApi();
  const settings = useQuery({ queryKey: ['orderSettings', marketId], queryFn: () => api.orderSettings(marketId) });
  if (settings.isPending) {
    return <p>{t('app.loading')}</p>;
  }
  if (settings.isError) {
    return (
      <p className="error" role="alert">
        {errorMessage(t, settings.error)}
      </p>
    );
  }
  return <SettingsEditor key={marketId} marketId={marketId} stored={settings.data} />;
}

function SettingsEditor({ marketId, stored }: { marketId: string; stored: MerchantOrderSettings }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<SettingsForm>(() => fromSettings(stored));
  const [problems, setProblems] = useState<SettingsProblems>({});
  const [saved, setSaved] = useState(false);
  const [pauseUntilInput, setPauseUntilInput] = useState('');

  const store = (updated: MerchantOrderSettings) => queryClient.setQueryData(['orderSettings', marketId], updated);
  const save = useMutation({
    mutationFn: (request: OrderSettings) => api.updateOrderSettings(marketId, request),
    onSuccess: (updated) => {
      store(updated);
      setForm(fromSettings(updated));
      setSaved(true);
    },
  });
  // The pause takes effect at once, independent of unsaved changes in the form.
  const pause = useMutation({
    mutationFn: (until: string | null) => api.updateOrderSettings(marketId, withPause(stored, until)),
    onSuccess: store,
  });

  const change = <K extends keyof SettingsForm>(key: K, value: SettingsForm[K]) => {
    setSaved(false);
    setForm((current) => ({ ...current, [key]: value }));
  };
  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    const result = toSettings(form, stored.pausedUntil ?? null);
    if ('problems' in result) {
      setProblems(result.problems);
      return;
    }
    setProblems({});
    save.mutate(result.settings);
  };
  const problem = (key: keyof SettingsForm) => {
    const message = problems[key];
    return message === undefined ? null : (
      <span className="error small" id={`settings-problem-${key}`}>
        {t(message)}
      </span>
    );
  };
  const described = (key: keyof SettingsForm) => (problems[key] === undefined ? {} : { 'aria-invalid': true, 'aria-describedby': `settings-problem-${key}` });
  const dateTime = (iso: string) => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-u-nu-latn' : i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
  const paused = stored.pausedUntil != null && new Date(stored.pausedUntil) > new Date();

  const numberField = (key: 'deliveryCapacity' | 'pickupCapacity' | 'deliveryLeadMinutes' | 'pickupLeadMinutes' | 'daysAhead' | 'defaultMaxOrderQuantity', label: string, fallback: number) => (
    <div className="stack tight" style={{ width: '13em' }}>
      <label className="field">
        {label}
        <input inputMode="numeric" dir="ltr" value={form[key]} placeholder={t('settings.default', { value: fallback })} onChange={(e) => change(key, e.target.value)} {...described(key)} />
      </label>
      {problem(key)}
    </div>
  );

  return (
    <form className="stack" onSubmit={submit} aria-labelledby="settings-title" noValidate>
      <h1 id="settings-title">{t('settings.title')}</h1>
      <p className="muted small">{t('settings.hint')}</p>

      <section className="card stack" aria-labelledby="settings-online">
        <h2 id="settings-online">{t('settings.online')}</h2>
        <label className="chip" style={{ alignSelf: 'flex-start' }}>
          <input type="checkbox" checked={form.ordersEnabled} onChange={(e) => change('ordersEnabled', e.target.checked)} />
          {t('settings.ordersEnabled')}
        </label>
        <p className="muted small">{t(form.ordersEnabled ? 'settings.ordersOnHint' : 'settings.ordersOffHint')}</p>
      </section>

      <section className="card stack" aria-labelledby="settings-pause">
        <h2 id="settings-pause">{t('settings.pause')}</h2>
        <p className="muted small">{t('settings.pauseHint')}</p>
        {paused ? (
          <div className="row">
            <strong role="status">{t('settings.pausedUntil', { time: dateTime(stored.pausedUntil ?? '') })}</strong>
            <button className="button" type="button" disabled={pause.isPending} onClick={() => pause.mutate(null)}>
              {t('settings.endPause')}
            </button>
          </div>
        ) : (
          <div className="row">
            <button className="button secondary" type="button" disabled={pause.isPending} onClick={() => pause.mutate(new Date(Date.now() + 60 * 60 * 1000).toISOString())}>
              {t('settings.pauseHour')}
            </button>
            <button className="button secondary" type="button" disabled={pause.isPending} onClick={() => pause.mutate(endOfDay(new Date()).toISOString())}>
              {t('settings.pauseToday')}
            </button>
            <label className="field">
              {t('settings.pauseUntil')}
              <input type="datetime-local" dir="ltr" value={pauseUntilInput} onChange={(e) => setPauseUntilInput(e.target.value)} />
            </label>
            <button
              className="button secondary"
              type="button"
              disabled={pause.isPending || pauseUntilInput === '' || new Date(pauseUntilInput) <= new Date()}
              onClick={() => pause.mutate(new Date(pauseUntilInput).toISOString())}
            >
              {t('settings.startPause')}
            </button>
          </div>
        )}
        {pause.isError && (
          <p className="error" role="alert">
            {errorMessage(t, pause.error)}
          </p>
        )}
      </section>

      <fieldset className="stack plain" disabled={save.isPending}>
        <section className="card stack" aria-labelledby="settings-closed">
          <h2 id="settings-closed">{t('settings.closed')}</h2>
          <p className="muted small">{t('settings.closedHint')}</p>
          <label className="chip" style={{ alignSelf: 'flex-start' }}>
            <input type="checkbox" checked={form.closed} onChange={(e) => change('closed', e.target.checked)} />
            {t('settings.closedOn')}
          </label>
          {form.closed && (
            <div className="row" style={{ alignItems: 'flex-start' }}>
              <label className="field">
                {t('settings.closedFrom')}
                <input type="date" dir="ltr" value={form.closedFrom} onChange={(e) => change('closedFrom', e.target.value)} />
              </label>
              <div className="stack tight">
                <label className="field">
                  {t('settings.closedUntil')}
                  <input type="date" dir="ltr" value={form.closedUntil} onChange={(e) => change('closedUntil', e.target.value)} {...described('closedUntil')} />
                </label>
                {problem('closedUntil')}
              </div>
            </div>
          )}
        </section>

        <section className="card stack" aria-labelledby="settings-slots">
          <h2 id="settings-slots">{t('settings.slots')}</h2>
          <p className="muted small">{t('settings.slotsHint')}</p>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            {numberField('pickupCapacity', t('settings.pickupCapacity'), stored.defaults.pickupCapacity)}
            {numberField('deliveryCapacity', t('settings.deliveryCapacity'), stored.defaults.deliveryCapacity)}
          </div>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            {numberField('pickupLeadMinutes', t('settings.pickupLead'), stored.defaults.pickupLeadMinutes)}
            {numberField('deliveryLeadMinutes', t('settings.deliveryLead'), stored.defaults.deliveryLeadMinutes)}
            {numberField('daysAhead', t('settings.daysAhead'), stored.defaults.daysAhead)}
          </div>
        </section>

        <section className="card stack" aria-labelledby="settings-delivery-hours">
          <h2 id="settings-delivery-hours">{t('settings.deliveryHours')}</h2>
          <label className="chip" style={{ alignSelf: 'flex-start' }}>
            <input type="checkbox" checked={form.ownDeliveryHours} onChange={(e) => change('ownDeliveryHours', e.target.checked)} />
            {t('settings.ownDeliveryHours')}
          </label>
          {form.ownDeliveryHours ? (
            <HoursEditor hours={form.deliveryHours} onChange={(hours) => change('deliveryHours', hours)} />
          ) : (
            <p className="muted small">{t('settings.deliveryHoursHint')}</p>
          )}
          {problem('deliveryHours')}
        </section>

        <section className="card stack" aria-labelledby="settings-handling">
          <h2 id="settings-handling">{t('settings.handling')}</h2>
          <label className="chip" style={{ alignSelf: 'flex-start' }}>
            <input type="checkbox" checked={form.autoAccept} onChange={(e) => change('autoAccept', e.target.checked)} />
            {t('settings.autoAccept')}
          </label>
          <p className="muted small">{t('settings.autoAcceptHint')}</p>
          <div className="stack tight">
            <span>{t('settings.payment')}</span>
            <div className="row">
              {PAYMENT_METHODS.map((method) => {
                const checked = form.paymentMethods.includes(method);
                return (
                  <label key={method} className="chip">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => change('paymentMethods', checked ? form.paymentMethods.filter((m) => m !== method) : [...form.paymentMethods, method])}
                    />
                    {t(`settings.method.${method}`)}
                  </label>
                );
              })}
            </div>
            {problem('paymentMethods')}
            <p className="muted small">{t('settings.paymentHint')}</p>
          </div>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            {numberField('defaultMaxOrderQuantity', t('settings.defaultMax'), stored.defaults.maxOrderQuantity)}
          </div>
          <p className="muted small">{t('settings.defaultMaxHint')}</p>
        </section>
      </fieldset>

      {Object.keys(problems).length > 0 && (
        <p className="error" role="alert">
          {t('profile.problems', { n: Object.keys(problems).length })}
        </p>
      )}
      {save.isError && (
        <p className="error" role="alert">
          {errorMessage(t, save.error)}
        </p>
      )}
      {saved && (
        <p className="success" role="status">
          {t('settings.saved')}
        </p>
      )}
      <div>
        <button className="button" type="submit" disabled={save.isPending}>
          {t('settings.save')}
        </button>
      </div>
    </form>
  );
}
