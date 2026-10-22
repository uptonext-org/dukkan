import { product } from '../../../test/fixtures';
import type { ShoppingListLine } from '../../../shared/api';
import { appendText, cartUpdates, initialSelections, speechLanguage } from '../model';

const apples = product({ id: 'a', name: 'Äpfel', maxOrderQuantity: 5 });
const pears = product({ id: 'p', name: 'Birnen', maxOrderQuantity: 5 });

const lines: ShoppingListLine[] = [
  {
    text: '3 Äpfel',
    amount: null,
    match: { product: apples, quantity: 3 },
    alternatives: [{ product: pears, quantity: 2 }],
  },
  { text: 'noch 2 Äpfel', amount: null, match: { product: apples, quantity: 2 }, alternatives: [] },
  { text: 'Einhorn', amount: null, match: null, alternatives: [] },
];

describe('shopping list model', () => {
  it('selects every matched line with its suggested quantity', () => {
    expect(initialSelections({ engine: 'AI', lines })).toEqual([
      { selected: true, choice: 0, quantity: 3 },
      { selected: true, choice: 0, quantity: 2 },
      { selected: false, choice: 0, quantity: 1 },
    ]);
  });

  it('adds lines for the same product to what is in the cart, within the order limit', () => {
    const selections = initialSelections({ engine: 'AI', lines });
    expect(cartUpdates(lines, selections, new Map([['a', 1]]))).toEqual([
      { productId: 'a', name: 'Äpfel', quantity: 5 },
    ]);
    const pearsInstead = [{ selected: true, choice: 1, quantity: 2 }, ...selections.slice(1)];
    expect(cartUpdates(lines, pearsInstead, new Map())).toEqual([
      { productId: 'p', name: 'Birnen', quantity: 2 },
      { productId: 'a', name: 'Äpfel', quantity: 2 },
    ]);
    // Nothing to do when the cart already holds the maximum.
    expect(cartUpdates(lines, selections, new Map([['a', 5]]))).toEqual([]);
  });

  it('appends dictated text and picks the recogniser language', () => {
    expect(appendText('', ' 2 Gurken ')).toBe('2 Gurken');
    expect(appendText('2 Gurken', 'Milch')).toBe('2 Gurken, Milch');
    expect(appendText('2 Gurken,', 'Milch')).toBe('2 Gurken, Milch');
    expect(appendText('2 Gurken', '')).toBe('2 Gurken');
    expect([speechLanguage('de'), speechLanguage('en'), speechLanguage('ar')]).toEqual([
      'de-DE',
      'en-US',
      'ar-SA',
    ]);
  });
});
