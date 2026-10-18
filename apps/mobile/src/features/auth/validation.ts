// Pragmatic email check; the server performs the authoritative validation.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type FieldErrors<K extends string> = { [P in K]?: string | undefined };

export interface LoginValues {
  email: string;
  password: string;
}

export function validateLogin(values: LoginValues): FieldErrors<keyof LoginValues> {
  const errors: FieldErrors<keyof LoginValues> = {};
  const email = values.email.trim();
  if (email === '') {
    errors.email = 'Please enter your email address.';
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Please enter a valid email address.';
  }
  if (values.password === '') {
    errors.password = 'Please enter your password.';
  }
  return errors;
}

export interface RegisterValues extends LoginValues {
  firstName: string;
  lastName: string;
}

export const MIN_PASSWORD_LENGTH = 10;

export function validateRegister(values: RegisterValues): FieldErrors<keyof RegisterValues> {
  const errors: FieldErrors<keyof RegisterValues> = {};
  const email = values.email.trim();
  if (values.firstName.trim() === '') {
    errors.firstName = 'Please enter your first name.';
  } else if (values.firstName.trim().length > 100) {
    errors.firstName = 'First name is too long.';
  }
  if (values.lastName.trim() === '') {
    errors.lastName = 'Please enter your last name.';
  } else if (values.lastName.trim().length > 100) {
    errors.lastName = 'Last name is too long.';
  }
  if (email === '') {
    errors.email = 'Please enter your email address.';
  } else if (!EMAIL_PATTERN.test(email) || email.length > 254) {
    errors.email = 'Please enter a valid email address.';
  }
  if (values.password.length < MIN_PASSWORD_LENGTH) {
    errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
  } else if (values.password.length > 128) {
    errors.password = 'Password must be at most 128 characters.';
  }
  return errors;
}

export function hasErrors(errors: Record<string, string | undefined>): boolean {
  return Object.values(errors).some((message) => message !== undefined);
}
