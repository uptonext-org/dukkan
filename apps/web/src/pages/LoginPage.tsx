import { useState, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { useApi } from '../api/ApiContext';
import { ApiError } from '../api/client';
import { errorMessage } from '../ui/errors';

export function LoginPage() {
  const { t } = useTranslation();
  const api = useApi();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.login(email.trim(), password, (user) => user.roles.includes('MERCHANT') || user.roles.includes('ADMIN'));
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'NO_PORTAL_ACCESS' ? t('login.noAccess') : errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="login">
      <form className="card stack" onSubmit={(e) => void submit(e)} aria-labelledby="login-title">
        <div>
          <h1 id="login-title">{t('app.title')}</h1>
          <p className="muted">{t('app.subtitle')}</p>
        </div>
        <label className="field">
          {t('login.email')}
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} dir="ltr" />
        </label>
        <label className="field">
          {t('login.password')}
          <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} dir="ltr" />
        </label>
        {error !== null && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button className="button" type="submit" disabled={busy}>
          {t('login.submit')}
        </button>
        <p className="muted small">{t('login.hint')}</p>
      </form>
    </main>
  );
}
