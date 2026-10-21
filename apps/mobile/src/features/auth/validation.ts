import type de from '../../i18n/locales/de.json';

/** Keys under `validation.*` in the locale files. Validators return keys; screens translate them. */
export type ValidationKey = keyof (typeof de)['validation'];

export type FieldErrors<K extends string> = { [P in K]?: ValidationKey | undefined };

// Pragmatic email check; the server performs the authoritative validation.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const MIN_PASSWORD_LENGTH = 10;

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
  } else if (values.password.length > 128) {
    errors.password = 'tooLong';
  }
  return errors;
}

export function hasErrors(errors: Record<string, string | undefined>): boolean {
  return Object.values(errors).some((message) => message !== undefined);
}
