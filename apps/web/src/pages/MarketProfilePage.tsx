import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ChangeEvent, type ReactNode, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import { ApiError } from '../api/client';
import type { MerchantMarket, Place } from '../api/types';
import { useMerchantLanguages } from '../api/useMerchantLanguages';
import { LANGUAGES } from '../i18n';
import { errorMessage } from '../ui/errors';
import { formField, fromMarket, MARKET_TAGS, MAX_CUISINES, MAX_INTERVALS_PER_DAY, toRequest, type Interval, type ProfileForm, type ProfileProblems } from './marketProfile';

const SERVER_CODES: Record<string, string> = {
  Overlap: 'profile.problem.overlap',
  NotEqualOpensAt: 'profile.problem.sameTime',
  FulfilmentRequired: 'profile.problem.fulfilment',
  Time: 'profile.problem.time',
};

function weekdayNames(language: string): string[] {
  const format = new Intl.DateTimeFormat(language === 'ar' ? 'ar-u-nu-latn' : language, { weekday: 'long', timeZone: 'UTC' });
  // 1 January 2024 was a Monday.
  return Array.from({ length: 7 }, (_, i) => format.format(new Date(Date.UTC(2024, 0, 1 + i))));
}

/** The market's public profile: names, address, delivery and pickup, opening hours, imprint, images. */
export function MarketProfilePage({ market }: { market: MerchantMarket }) {
  const { i18n } = useTranslation();
  // A new form when another market is picked; the language only matters for the first fill.
  return <ProfileEditor key={market.id} market={market} language={i18n.language} />;
}

function ProfileEditor({ market, language }: { market: MerchantMarket; language: string }) {
  const { t, i18n } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<ProfileForm>(() => fromMarket(market, language));
  const [problems, setProblems] = useState<ProfileProblems>({});
  const [saved, setSaved] = useState(false);
  // A server error that belongs to no field of the form still has to be shown.
  const [unplacedError, setUnplacedError] = useState(false);
  const types = useQuery({ queryKey: ['marketTypes', i18n.language], queryFn: () => api.marketTypes() });
  const cuisines = useQuery({ queryKey: ['cuisines', i18n.language], queryFn: () => api.cuisines() });

  const change = <K extends keyof ProfileForm>(key: K, value: ProfileForm[K]) => {
    setSaved(false);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const save = useMutation({
    mutationFn: (request: Parameters<typeof api.updateMarket>[1]) => api.updateMarket(market.id, request),
    onSuccess: (updated) => {
      setSaved(true);
      setForm(fromMarket(updated, i18n.language));
      void queryClient.invalidateQueries({ queryKey: ['myMarkets'] });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.problem?.errors !== undefined) {
        const fromServer: ProfileProblems = {};
        for (const e of error.problem.errors) {
          const field = formField(e.field);
          if (field !== null && fromServer[field] === undefined) {
            fromServer[field] = SERVER_CODES[e.code] ?? 'profile.problem.invalid';
          }
        }
        setProblems(fromServer);
        setUnplacedError(Object.keys(fromServer).length === 0);
      }
    },
  });

  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    setSaved(false);
    const result = toRequest(form);
    if ('problems' in result) {
      setProblems(result.problems);
      return;
    }
    setProblems({});
    setUnplacedError(false);
    save.mutate(result.request);
  };

  // Seller information the platform needs before it approves the market (imprint, Art. 30 DSA).
  const review = save.data?.review ?? market.review;
  const missing = review?.items.filter((item) => item.required && !item.ok).map((item) => item.code) ?? [];
  const problemCount = Object.keys(problems).length;
  const problem = (key: keyof ProfileForm) => {
    const message = problems[key];
    return message === undefined ? null : (
      <span className="error small" id={`problem-${key}`}>
        {t(message)}
      </span>
    );
  };
  const described = (key: keyof ProfileForm) => (problems[key] === undefined ? {} : { 'aria-invalid': true, 'aria-describedby': `problem-${key}` });

  return (
    <form className="stack" onSubmit={submit} aria-labelledby="profile-title" aria-busy={save.isPending} noValidate>
      <h1 id="profile-title">{t('profile.title')}</h1>
      <p className="muted small">{t('profile.hint')}</p>
      {missing.length > 0 && (
        <p className="error small" role="note">
          {t('profile.reviewMissing', { items: missing.map((code) => t(`admin.check.${code}`)).join(', ') })}
        </p>
      )}
      {/* Edits during a save would be replaced by the saved profile, so the form waits. */}
      <fieldset className="stack plain" disabled={save.isPending}>

      <section className="card stack" aria-labelledby="profile-basics">
        <h2 id="profile-basics">{t('profile.basics')}</h2>
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <div className="stack tight" style={{ flex: '2 1 240px' }}>
            <label className="field">
              {t('profile.name')}
              <input value={form.name} maxLength={80} onChange={(e) => change('name', e.target.value)} {...described('name')} />
            </label>
            {problem('name')}
          </div>
          <div className="stack tight" style={{ flex: '1 1 180px' }}>
            <label className="field">
              {t('profile.type')}
              <select value={form.typeCode} onChange={(e) => change('typeCode', e.target.value)} {...described('typeCode')}>
                {form.typeCode === '' && <option value="">–</option>}
                {(types.data ?? [market.type]).map((type) => (
                  <option key={type.code} value={type.code}>
                    {type.name}
                  </option>
                ))}
              </select>
            </label>
            {problem('typeCode')}
          </div>
          <label className="field" style={{ flex: '1 1 140px' }}>
            {t('profile.primaryLocale')}
            <select value={form.primaryLocale} onChange={(e) => change('primaryLocale', e.target.value)} {...described('primaryLocale')}>
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code} lang={l.code}>
                  {l.nativeName}
                </option>
              ))}
            </select>
          </label>
        </div>
        {problem('primaryLocale')}
        <fieldset className="stack plain">
          <legend className="small muted">{t('profile.cuisines', { n: form.cuisineCodes.length, max: MAX_CUISINES })}</legend>
          <div className="chips">
            {(cuisines.data ?? []).map((cuisine) => {
              const checked = form.cuisineCodes.includes(cuisine.code);
              return (
                <label key={cuisine.code} className="chip">
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={!checked && form.cuisineCodes.length >= MAX_CUISINES}
                    onChange={() => change('cuisineCodes', checked ? form.cuisineCodes.filter((c) => c !== cuisine.code) : [...form.cuisineCodes, cuisine.code])}
                  />
                  {cuisine.name}
                </label>
              );
            })}
          </div>
          {problem('cuisineCodes')}
        </fieldset>
        <fieldset className="stack plain">
          <legend className="small muted">{t('profile.tags')}</legend>
          <div className="chips">
            {MARKET_TAGS.map((tag) => {
              const checked = form.tags.includes(tag);
              return (
                <label key={tag} className="chip">
                  <input type="checkbox" checked={checked} onChange={() => change('tags', checked ? form.tags.filter((x) => x !== tag) : [...form.tags, tag])} />
                  {t(`profile.tag.${tag}`)}
                </label>
              );
            })}
          </div>
        </fieldset>
        <Descriptions form={form} onChange={(descriptions) => change('descriptions', descriptions)} described={described('descriptions')} />
        {problem('descriptions')}
      </section>

      <section className="card stack" aria-labelledby="profile-images">
        <h2 id="profile-images">{t('profile.images')}</h2>
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <ImageField marketId={market.id} purpose="LOGO" label={t('profile.logo')} url={form.logoUrl} onChange={(url) => change('logoUrl', url)} />
          <ImageField marketId={market.id} purpose="COVER" label={t('profile.cover')} url={form.coverImageUrl} onChange={(url) => change('coverImageUrl', url)} />
        </div>
        {problem('logoUrl')}
        {problem('coverImageUrl')}
      </section>

      <section className="card stack" aria-labelledby="profile-contact">
        <h2 id="profile-contact">{t('profile.contact')}</h2>
        <AddressField
          value={form.addressLine}
          located={form.location !== null}
          problemId={problems.addressLine === undefined ? undefined : 'problem-addressLine'}
          onType={(addressLine) => {
            setSaved(false);
            // A typed address has no position until a suggestion is picked.
            setForm((current) => ({ ...current, addressLine, location: null }));
          }}
          onPick={(place) => {
            setSaved(false);
            setForm((current) => ({ ...current, addressLine: place.label, location: place.location }));
          }}
        />
        {problem('addressLine')}
        <div className="stack tight" style={{ maxWidth: 280 }}>
          <label className="field">
            {t('profile.phone')}
            <input type="tel" dir="ltr" autoComplete="tel" value={form.phone} maxLength={30} onChange={(e) => change('phone', e.target.value)} {...described('phone')} />
          </label>
          {problem('phone')}
        </div>
      </section>

      <section className="card stack" aria-labelledby="profile-fulfilment">
        <h2 id="profile-fulfilment">{t('profile.fulfilment')}</h2>
        <div className="row">
          <label className="chip">
            <input type="checkbox" checked={form.pickup} onChange={(e) => change('pickup', e.target.checked)} />
            {t('profile.pickup')}
          </label>
          <label className="chip">
            <input type="checkbox" checked={form.delivery} onChange={(e) => change('delivery', e.target.checked)} />
            {t('profile.delivery')}
          </label>
        </div>
        {problem('delivery')}
        <div className="row" style={{ alignItems: 'flex-start' }}>
          {form.delivery && (
            <>
              <AmountField label={t('profile.radius')} value={form.radiusKm} onChange={(v) => change('radiusKm', v)} problem={problem('radiusKm')} described={described('radiusKm')} />
              <AmountField label={t('profile.fee')} value={form.fee} onChange={(v) => change('fee', v)} problem={problem('fee')} described={described('fee')} />
              <AmountField label={t('profile.freeFrom')} value={form.freeFrom} onChange={(v) => change('freeFrom', v)} problem={problem('freeFrom')} described={described('freeFrom')} />
            </>
          )}
          <AmountField label={t('profile.minimum')} value={form.minimum} onChange={(v) => change('minimum', v)} problem={problem('minimum')} described={described('minimum')} />
        </div>
      </section>

      <section className="card stack" aria-labelledby="profile-hours">
        <h2 id="profile-hours">{t('profile.hours')}</h2>
        <p className="muted small">{t('profile.hoursHint')}</p>
        <HoursEditor hours={form.hours} onChange={(hours) => change('hours', hours)} />
        {problem('hours')}
      </section>

      <section className="card stack" aria-labelledby="profile-imprint">
        <h2 id="profile-imprint">{t('profile.imprint')}</h2>
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <div className="stack tight" style={{ flex: '2 1 240px' }}>
            <label className="field">
              {t('profile.legalName')}
              <input value={form.legalName} maxLength={120} onChange={(e) => change('legalName', e.target.value)} {...described('legalName')} />
            </label>
            {problem('legalName')}
          </div>
          <div className="stack tight" style={{ flex: '1 1 200px' }}>
            <label className="field">
              {t('profile.registerInfo')}
              <input value={form.registerInfo} maxLength={120} onChange={(e) => change('registerInfo', e.target.value)} {...described('registerInfo')} />
            </label>
            {problem('registerInfo')}
          </div>
        </div>
      </section>

      </fieldset>
      {problemCount > 0 && (
        <p className="error" role="alert">
          {t('profile.problems', { n: problemCount })}
        </p>
      )}
      {save.isError && (unplacedError || !(save.error instanceof ApiError && save.error.problem?.errors !== undefined)) && (
        <p className="error" role="alert">
          {errorMessage(t, save.error)}
        </p>
      )}
      {saved && (
        <p className="success" role="status">
          {t('profile.saved')}
        </p>
      )}
      <div>
        <button className="button" type="submit" disabled={save.isPending}>
          {t('profile.save')}
        </button>
      </div>
    </form>
  );
}

function AmountField({ label, value, onChange, problem, described }: { label: string; value: string; onChange: (value: string) => void; problem: ReactNode; described: object }) {
  return (
    <div className="stack tight" style={{ width: '10em' }}>
      <label className="field">
        {label}
        <input inputMode="decimal" dir="ltr" value={value} onChange={(e) => onChange(e.target.value)} {...described} />
      </label>
      {problem}
    </div>
  );
}

function Descriptions({ form, onChange, described }: { form: ProfileForm; onChange: (descriptions: Record<string, string>) => void; described: object }) {
  const { t } = useTranslation();
  const api = useApi();
  const { languages, multilingual } = useMerchantLanguages(form.primaryLocale);
  const filled = LANGUAGES.filter((l) => (form.descriptions[l.code] ?? '').trim() !== '');
  const translate = useMutation({
    mutationFn: () => api.translate('MARKET_DESCRIPTION', Object.fromEntries(filled.map((l): [string, string] => [l.code, form.descriptions[l.code] ?? '']))),
    onSuccess: (result) => {
      const suggestions = result.fields.find((f) => f.key === 'text')?.values ?? {};
      const next = { ...form.descriptions };
      for (const [code, text] of Object.entries(suggestions)) {
        if ((next[code] ?? '').trim() === '') {
          next[code] = text;
        }
      }
      onChange(next);
    },
  });
  return (
    <div className="stack">
      <div className="row" style={{ alignItems: 'flex-start' }}>
        {languages.map((l) => (
          <label key={l.code} className="field" style={{ flex: '1 1 240px' }}>
            {multilingual ? `${t('profile.description')} · ${l.nativeName}${l.code === form.primaryLocale ? ` (${t('promotions.primary')})` : ''}` : t('profile.description')}
            <textarea rows={4} dir={l.direction} lang={l.code} maxLength={2000} value={form.descriptions[l.code] ?? ''} onChange={(e) => onChange({ ...form.descriptions, [l.code]: e.target.value })} {...described} />
          </label>
        ))}
      </div>
      {multilingual && filled.length > 0 && filled.length < LANGUAGES.length && (
        <div>
          <button className="button secondary" type="button" disabled={translate.isPending} onClick={() => translate.mutate()}>
            {t('promotions.translate')}
          </button>
        </div>
      )}
      {translate.isError && (
        <p className="error small" role="alert">
          {errorMessage(t, translate.error)}
        </p>
      )}
    </div>
  );
}

function AddressField({ value, located, problemId, onType, onPick }: { value: string; located: boolean; problemId: string | undefined; onType: (text: string) => void; onPick: (place: Place) => void }) {
  const { t } = useTranslation();
  const api = useApi();
  const search = useMutation({ mutationFn: (q: string) => api.searchPlaces(q) });
  const find = () => {
    if (value.trim().length >= 2) {
      search.mutate(value.trim());
    }
  };
  return (
    <div className="stack">
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: '1 1 320px' }}>
          {t('profile.address')}
          <input
            value={value}
            maxLength={200}
            autoComplete="street-address"
            onChange={(e) => onType(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                find();
              }
            }}
            {...(problemId === undefined ? {} : { 'aria-invalid': true, 'aria-describedby': problemId })}
          />
        </label>
        <button className="button secondary" type="button" disabled={search.isPending} onClick={find}>
          {t('profile.findAddress')}
        </button>
      </div>
      <p className="small muted">{located ? t('profile.located') : t('profile.notLocated')}</p>
      {search.isError && (
        <p className="error small" role="alert">
          {errorMessage(t, search.error)}
        </p>
      )}
      {search.data !== undefined && (
        <ul className="suggestions" aria-label={t('profile.suggestions')}>
          {search.data.length === 0 && <li className="muted small">{t('profile.noPlaces')}</li>}
          {search.data.map((place) => (
            <li key={`${place.label}-${place.location.lat}-${place.location.lng}`}>
              <button
                type="button"
                className="link"
                onClick={() => {
                  onPick(place);
                  search.reset();
                }}
              >
                {place.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ImageField({ marketId, purpose, label, url, onChange }: { marketId: string; purpose: 'LOGO' | 'COVER'; label: string; url: string | null; onChange: (url: string | null) => void }) {
  const { t } = useTranslation();
  const api = useApi();
  const upload = useMutation({
    mutationFn: (file: File) => api.uploadMedia(marketId, file, purpose),
    onSuccess: (media) => onChange(media.url),
  });
  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file !== undefined) {
      upload.mutate(file);
    }
  };
  return (
    <div className="stack" style={{ flex: '1 1 240px' }}>
      <span className="small">{label}</span>
      {url !== null ? <img className={purpose === 'LOGO' ? 'logo-preview' : 'cover-preview'} src={url} alt={label} /> : <span className="muted small">{t('profile.noImage')}</span>}
      <div className="row">
        <label className="button secondary">
          {upload.isPending ? t('app.loading') : t('profile.upload')}
          <input className="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" aria-label={label} disabled={upload.isPending} onChange={pick} />
        </label>
        {url !== null && (
          <button className="button secondary" type="button" onClick={() => onChange(null)}>
            {t('profile.removeImage')}
          </button>
        )}
      </div>
      {upload.isError && (
        <p className="error small" role="alert">
          {errorMessage(t, upload.error)}
        </p>
      )}
    </div>
  );
}

export function HoursEditor({ hours, onChange }: { hours: Interval[][]; onChange: (hours: Interval[][]) => void }) {
  const { t, i18n } = useTranslation();
  const days = weekdayNames(i18n.language);
  const setDay = (index: number, intervals: Interval[]) => onChange(hours.map((day, i) => (i === index ? intervals : day)));
  return (
    <div className="table-wrap">
      <table>
        <tbody>
          {hours.map((intervals, index) => {
            const day = days[index] ?? '';
            return (
              <tr key={index}>
                <th scope="row" style={{ width: '9em' }}>
                  {day}
                </th>
                <td>
                  <div className="stack" style={{ gap: 6 }}>
                    {intervals.length === 0 && <span className="muted small">{t('profile.closed')}</span>}
                    {intervals.map((interval, i) => (
                      <div className="row" key={i} style={{ alignItems: 'center' }}>
                        <input
                          type="time"
                          dir="ltr"
                          aria-label={t('profile.opensAt', { day })}
                          value={interval.opensAt}
                          onChange={(e) => setDay(index, intervals.map((x, j) => (j === i ? { ...x, opensAt: e.target.value } : x)))}
                        />
                        <span aria-hidden="true">–</span>
                        <input
                          type="time"
                          dir="ltr"
                          aria-label={t('profile.closesAt', { day })}
                          value={interval.closesAt}
                          onChange={(e) => setDay(index, intervals.map((x, j) => (j === i ? { ...x, closesAt: e.target.value } : x)))}
                        />
                        <button className="button secondary" type="button" aria-label={t('profile.removeInterval', { day })} onClick={() => setDay(index, intervals.filter((_, j) => j !== i))}>
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                </td>
                <td style={{ width: '1%', whiteSpace: 'nowrap' }}>
                  {intervals.length < MAX_INTERVALS_PER_DAY && (
                    <button
                      className="button secondary"
                      type="button"
                      aria-label={t('profile.addInterval', { day })}
                      onClick={() => setDay(index, [...intervals, intervals.length === 0 ? { opensAt: '09:00', closesAt: '18:00' } : { opensAt: '', closesAt: '' }])}
                    >
                      +
                    </button>
                  )}
                  {index > 0 && (hours[index - 1] ?? []).length > 0 && (
                    <button
                      className="button secondary"
                      type="button"
                      aria-label={t('profile.copyPreviousFor', { day })}
                      onClick={() => setDay(index, (hours[index - 1] ?? []).map((x) => ({ ...x })))}
                    >
                      {t('profile.copyPrevious')}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
