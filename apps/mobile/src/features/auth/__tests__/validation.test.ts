import { createI18n } from '../../../shared/i18n';
import { countUtf8Bytes, utf8ByteLength, validateRegister } from '../validation';

const base = { email: 'a@b.de', firstName: 'Ada', lastName: 'Lovelace' };

describe('password length in UTF-8 bytes (bcrypt limit, contract: at most 72 bytes)', () => {
  it('counts 1 byte for ASCII, 2 for Arabic and umlauts, 3 for €, 4 for emoji', () => {
    for (const count of [countUtf8Bytes, utf8ByteLength]) {
      expect(count('abc')).toBe(3);
      expect(count('ü')).toBe(2);
      expect(count('سلام')).toBe(8);
      expect(count('€')).toBe(3);
      expect(count('🍎')).toBe(4);
    }
  });

  it('accepts 72 ASCII characters and 36 Arabic letters, rejects one byte more', () => {
    expect(validateRegister({ ...base, password: 'a'.repeat(72) }).password).toBeUndefined();
    expect(validateRegister({ ...base, password: 'a'.repeat(73) }).password).toBe('passwordTooLong');
    expect(validateRegister({ ...base, password: 'س'.repeat(36) }).password).toBeUndefined();
    // 37 Arabic letters are only 37 characters (well under 128) but 74 bytes.
    expect(validateRegister({ ...base, password: 'س'.repeat(37) }).password).toBe('passwordTooLong');
    expect(validateRegister({ ...base, password: 'short' }).password).toBe('passwordTooShort');
  });

  it('explains that Arabic characters count double', () => {
    const de = createI18n('de');
    expect(de.t('validation.passwordTooLong')).toMatch(/^Passwort ist zu lang/);
    expect(de.t('validation.passwordTooLong')).toContain('arabische Zeichen zählen doppelt');
  });
});
