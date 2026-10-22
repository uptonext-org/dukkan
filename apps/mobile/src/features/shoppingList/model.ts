import type { ShoppingListChoice, ShoppingListLine, ShoppingListResult } from '../../shared/api';

/** What the customer chose for one line: whether to add it, which product and how many. */
export interface LineSelection {
  selected: boolean;
  /** Index into {@link choicesOf}. */
  choice: number;
  quantity: number;
}

export interface CartUpdate {
  productId: string;
  name: string;
  /** The new quantity in the cart (what was there plus what the list adds). */
  quantity: number;
}

export function choicesOf(line: ShoppingListLine): ShoppingListChoice[] {
  return line.match === null ? [] : [line.match, ...line.alternatives];
}

/** Every matched line starts selected with the best product and its suggested quantity. */
export function initialSelections(result: ShoppingListResult): LineSelection[] {
  return result.lines.map((line) => ({
    selected: line.match !== null,
    choice: 0,
    quantity: line.match?.quantity ?? 1,
  }));
}

export function chosen(line: ShoppingListLine, selection: LineSelection): ShoppingListChoice | undefined {
  return choicesOf(line)[selection.choice];
}

/**
 * The cart quantities to set: lines for the same product are added up, on top of what is already
 * in the cart, and capped at the product's order limit.
 */
export function cartUpdates(
  lines: ShoppingListLine[],
  selections: LineSelection[],
  inCart: ReadonlyMap<string, number>,
): CartUpdate[] {
  const updates = new Map<string, CartUpdate>();
  lines.forEach((line, i) => {
    const selection = selections[i];
    const choice = selection === undefined ? undefined : chosen(line, selection);
    if (selection === undefined || !selection.selected || choice === undefined || selection.quantity <= 0) {
      return;
    }
    const { product } = choice;
    const before = updates.get(product.id)?.quantity ?? inCart.get(product.id) ?? 0;
    updates.set(product.id, {
      productId: product.id,
      name: product.name,
      quantity: Math.min(product.maxOrderQuantity, before + selection.quantity),
    });
  });
  return [...updates.values()].filter((u) => u.quantity !== (inCart.get(u.productId) ?? 0));
}

/** The recogniser's language: the app language with a common region. */
export function speechLanguage(lang: string): string {
  switch (lang) {
    case 'ar':
      return 'ar-SA';
    case 'en':
      return 'en-US';
    default:
      return 'de-DE';
  }
}

/** Dictated text is appended to what is already in the field. */
export function appendText(existing: string, spoken: string): string {
  const a = existing.trim();
  const b = spoken.trim();
  if (a === '') {
    return b;
  }
  if (b === '') {
    return a;
  }
  return /[,.;\n]$/.test(a) ? `${a} ${b}` : `${a}, ${b}`;
}
