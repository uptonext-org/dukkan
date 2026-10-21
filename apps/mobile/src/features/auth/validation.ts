import type de from '../../i18n/locales/de.json';

/** Keys under `validation.*` in the locale files. Validators return keys; screens translate them. */
export type ValidationKey = keyof (typeof de)['validation'];

export type FieldErrors<K extends string> = { [P in K]?: ValidationKey | undefined };

// Pragmatic email check; the server performs the authoritative validation.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const MIN_PASSWORD_LENGTH = 10;
export const MAX_PASSWORD_LENGTH = 128;
/** bcrypt only uses the first 72 bytes; the server rejects longer passwords (MaxUtf8Bytes). */
export const MAX_PASSWORD_UTF8_BYTES = 72;

/** UTF-8 byte length counted by hand (code points: 1–4 bytes; lone surrogates as U+FFFD = 3). */
export function countUtf8Bytes(text: string): number {
  let bytes = 0;
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    if (code < 0x80) {
      bytes += 1;
    } else if (code < 0x800) {
      bytes += 2;
    } else if (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length && (text.charCodeAt(i + 1) & 0xfc00) === 0xdc00) {
      bytes += 4;
      i++;
    } else {
      bytes += 3;
    }
  }
  return bytes;
}

/** UTF-8 byte length: TextEncoder where the runtime has it (Hermes does), else the manual count. */
export function utf8ByteLength(text: string): number {
  const Encoder = (globalThis as { TextEncoder?: new () => { encode: (input: string) => Uint8Array } }).TextEncoder;
  if (Encoder !== undefined) {
    return new Encoder().encode(text).length;
  }
  return countUtf8Bytes(text);
}

export interface LoginValues {
  email: string;
  password: string;
}

export function validateLogin(values: LoginValues): FieldErrors<keyof LoginValues> {
  const errors: FieldErrors<keyof LoginValues> = {};
  const email = values.email.trim();
  if (email === '') {
    errors.email = 'emailRequired';
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'emailInvalid';
  }
  if (values.password === '') {
    errors.password = 'passwordRequired';
  }
  return errors;
}

export interface RegisterValues extends LoginValues {
  firstName: string;
  lastName: string;
}

export function validateRegister(values: RegisterValues): FieldErrors<keyof RegisterValues> {
  const errors: FieldErrors<keyof RegisterValues> = {};
  const email = values.email.trim();
  if (values.firstName.trim() === '') {
    errors.firstName = 'required';
  } else if (values.firstName.trim().length > 100) {
    errors.firstName = 'tooLong';
  }
  if (values.lastName.trim() === '') {
    errors.lastName = 'required';
  } else if (values.lastName.trim().length > 100) {
    errors.lastName = 'tooLong';
  }
  if (email === '') {
    errors.email = 'emailRequired';
  } else if (!EMAIL_PATTERN.test(email) || email.length > 254) {
    errors.email = 'emailInvalid';
  }
  if (values.password.length < MIN_PASSWORD_LENGTH) {
    errors.password = 'passwordTooShort';
  } else if (values.password.length > MAX_PASSWORD_LENGTH || utf8ByteLength(values.password) > MAX_PASSWORD_UTF8_BYTES) {
    // Arabic letters take 2 bytes each, so about 36 of them already reach the limit.
    errors.password = 'passwordTooLong';
  }
  return errors;
}

export function hasErrors(errors: Record<string, string | undefined>): boolean {
  return Object.values(errors).some((message) => message !== undefined);
}
