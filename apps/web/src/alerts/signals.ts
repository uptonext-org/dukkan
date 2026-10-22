/** How the portal gets a merchant's attention; replaceable in tests. */
export interface AlertSignals {
  /** A short chime. */
  sound: () => void;
  /** A system notification, only while the tab is in the background and permission was given. */
  notify: (title: string, body: string) => void;
  /** Asks for notification permission (from a click) and unlocks audio; false when notifications are blocked. */
  prepare: () => boolean;
}

let audio: AudioContext | null = null;

function context(): AudioContext | null {
  if (typeof window === 'undefined' || typeof window.AudioContext === 'undefined') {
    return null;
  }
  audio ??= new window.AudioContext();
  return audio;
}

/** Two short tones (no audio file needed). Browsers only play after a user gesture unlocked audio. */
function chime() {
  const ctx = context();
  if (ctx === null) {
    return;
  }
  if (ctx.state !== 'running') {
    // After a reload (or an iOS interruption) the context starts suspended; it resumes once the
    // page has had any user activation.
    ctx.resume().then(() => play(ctx), () => undefined);
    return;
  }
  play(ctx);
}

function play(ctx: AudioContext) {
  [880, 1320].forEach((frequency, i) => {
    const start = ctx.currentTime + i * 0.18;
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.16);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(start);
    oscillator.stop(start + 0.17);
  });
}

export const browserSignals: AlertSignals = {
  sound: chime,
  notify(title, body) {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted' || document.visibilityState === 'visible') {
      return;
    }
    try {
      new Notification(title, { body, tag: 'dukkan-new-order' });
    } catch {
      // Some browsers only allow notifications from a service worker; the tab title still shows it.
    }
  },
  prepare() {
    void context()?.resume().catch(() => undefined);
    if (typeof Notification === 'undefined') {
      return true;
    }
    if (Notification.permission === 'default') {
      void Notification.requestPermission();
    }
    return Notification.permission !== 'denied';
  },
};

const KEY = 'dukkan.orderAlerts';

/** Whether sound and notifications are on (per browser; off until the merchant switches them on). */
export function loadAlertsEnabled(): boolean {
  try {
    return localStorage.getItem(KEY) === 'on';
  } catch {
    return false;
  }
}

export function saveAlertsEnabled(enabled: boolean) {
  try {
    localStorage.setItem(KEY, enabled ? 'on' : 'off');
  } catch {
    // Storage blocked: the choice lasts until the tab is reloaded.
  }
}
