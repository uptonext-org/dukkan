import { render, screen } from '@testing-library/react-native';

import { formatMoney, formatMoneyForAccessibility } from '../../format/money';
import { Price } from '../Price';

// Intl uses a no-break space (U+00A0) between amount and symbol in de-DE.
const normalize = (s: string) => s.replace(/\u00a0/g, ' ');

describe('money formatting', () => {
  it('formats cents as EUR in de-DE', () => {
    expect(normalize(formatMoney({ amountCents: 249, currency: 'EUR' }))).toBe('2,49 €');
    expect(normalize(formatMoney({ amountCents: 0, currency: 'EUR' }))).toBe('0,00 €');
    expect(normalize(formatMoney({ amountCents: 123456, currency: 'EUR' }))).toBe('1.234,56 €');
    expect(normalize(formatMoney({ amountCents: 5, currency: 'EUR' }))).toBe('0,05 €');
  });

  it('produces a spoken label', () => {
    expect(formatMoneyForAccessibility({ amountCents: 249, currency: 'EUR' })).toBe('2,49 Euro');
    expect(formatMoneyForAccessibility({ amountCents: 199, currency: 'USD' })).toBe('1,99 USD');
  });
});

describe('<Price />', () => {
  it('renders the formatted amount with an accessible label', async () => {
    await render(<Price money={{ amountCents: 249, currency: 'EUR' }} accessibilityPrefix="Total" />);
    const price = screen.getByLabelText('Total 2,49 Euro');
    expect(price).toBeOnTheScreen();
    expect(normalize(String(price.props.children))).toBe('2,49 €');
  });
});
