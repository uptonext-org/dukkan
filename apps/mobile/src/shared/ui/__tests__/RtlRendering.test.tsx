import { screen, userEvent, within } from '@testing-library/react-native';
import { useState } from 'react';

import { MarketCard } from '../../../features/marketplace/components/MarketCard';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, MARKET_ID, product } from '../../../test/fixtures';
import { createTestDependencies, flatStyle, inheritedDirection, normalize, renderInAppRoot } from '../../../test/render';
import { ProductTile } from '../ProductTile';

const noop = () => undefined;
const hidden = { includeHiddenElements: true } as const;

function renderTile(language: 'de' | 'ar', quantity = 0, onChangeQuantity = jest.fn()) {
  const dependencies = createTestDependencies(createFakeApiClient({}).client, { language });
  return renderInAppRoot(
    <ProductTile product={product()} quantity={quantity} onPress={noop} onChangeQuantity={onChangeQuantity} />,
    dependencies,
  );
}

describe('ProductTile in RTL (ar)', () => {
  it('mirrors layout and the chevron, uses IBM Plex Sans Arabic and keeps de-DE prices', async () => {
    await renderTile('ar');
    const tile = screen.getByTestId(`product-tile-${product().id}`);

    expect(inheritedDirection(tile)).toBe('rtl');
    expect(within(tile).getByTestId('icon-chevron-mirrored', hidden)).toBeOnTheScreen();
    expect(within(tile).queryByTestId('icon-chevron', hidden)).not.toBeOnTheScreen();

    // "Bio" prefix translated, Arabic font for the name.
    const name = within(tile).getByText('Äpfel Pink Lady', { exact: false, ...hidden });
    expect(flatStyle(name).fontFamily).toBe('IBMPlexSansArabic-SemiBold');
    expect(within(tile).getByText('عضوي ', hidden)).toBeOnTheScreen();

    // Prices stay de-DE with Latin digits.
    expect(within(tile).getByText('2,79 €', { normalizer: normalize, ...hidden })).toBeOnTheScreen();
    expect(within(tile).getByText('3,49 €', { normalizer: normalize, ...hidden })).toBeOnTheScreen();
    expect(within(tile).getByText('550 g · 5,07 €/كغ', { normalizer: normalize, ...hidden })).toBeOnTheScreen();

    // Overlays are positioned with start/end, so they flip with the direction.
    const promoPill = within(tile).getByText('20 % Rabatt', hidden);
    type Host = { parent: Host | null; props: { style?: unknown } };
    let host: Host | null = promoPill;
    let positioned: Record<string, unknown> | undefined;
    while (host !== null && positioned === undefined) {
      const style = flatStyle(host);
      if (style.position === 'absolute') {
        positioned = style;
      }
      host = host.parent;
    }
    expect(positioned).toMatchObject({ start: 8 });
    expect(positioned).not.toHaveProperty('left');

    // Spoken summary is Arabic with Latin-digit euro amounts.
    expect(screen.getByRole('button', { name: /^عضوي Äpfel Pink Lady, süß-säuerlich, 2,79 Euro بدلًا من 3,49 Euro/ })).toBeOnTheScreen();
  });

  it('in German keeps LTR, Inter and an unmirrored chevron', async () => {
    await renderTile('de');
    const tile = screen.getByTestId(`product-tile-${product().id}`);
    expect(inheritedDirection(tile)).toBe('ltr');
    expect(within(tile).getByTestId('icon-chevron', hidden)).toBeOnTheScreen();
    expect(flatStyle(within(tile).getByText('Äpfel Pink Lady', { exact: false, ...hidden })).fontFamily).toBe('Inter-SemiBold');
    expect(within(tile).getByText('Bio ', hidden)).toBeOnTheScreen();
  });

  it('turns "+" into a − n + stepper bounded by maxOrderQuantity', async () => {
    const onChange = jest.fn();
    function Stateful() {
      const [quantity, setQuantity] = useState(0);
      return (
        <ProductTile
          product={product()}
          quantity={quantity}
          onPress={noop}
          onChangeQuantity={(p, next) => {
            onChange(p, next);
            setQuantity(next);
          }}
        />
      );
    }
    const dependencies = createTestDependencies(createFakeApiClient({}).client);
    await renderInAppRoot(<Stateful />, dependencies);
    const user = userEvent.setup();
    await user.press(screen.getByRole('button', { name: 'Äpfel Pink Lady in den Warenkorb' }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ id: product().id }), 1);
    expect(screen.queryByRole('button', { name: 'Äpfel Pink Lady in den Warenkorb' })).not.toBeOnTheScreen();
    expect(screen.getByLabelText('Menge 1')).toBeOnTheScreen();

    await user.press(screen.getByRole('button', { name: 'Mehr von Äpfel Pink Lady' }));
    await user.press(screen.getByRole('button', { name: 'Mehr von Äpfel Pink Lady' }));
    // maxOrderQuantity is 3 in the fixture.
    expect(screen.getByLabelText('Menge 3')).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Mehr von Äpfel Pink Lady' })).toBeDisabled();
    await user.press(screen.getByRole('button', { name: 'Weniger von Äpfel Pink Lady' }));
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ id: product().id }), 2);
  });
});

describe('MarketCard in RTL (ar)', () => {
  it('renders Arabic copy, keeps numbers de-DE and anchors the logo at the start corner', async () => {
    const dependencies = createTestDependencies(createFakeApiClient({}).client, { language: 'ar' });
    await renderInAppRoot(<MarketCard market={market()} onPress={noop} />, dependencies);
    const card = screen.getByTestId(`market-card-${MARKET_ID}`);
    expect(inheritedDirection(card)).toBe('rtl');
    expect(within(card).getByText('1,2 km · مفتوح حتى 20:00', { normalizer: normalize })).toBeOnTheScreen();
    expect(within(card).getByText('الحد الأدنى للطلب 15 € · التوصيل 2,99 €', { normalizer: normalize, ...hidden })).toBeOnTheScreen();
    expect(within(card).getByText('3 عروض', { normalizer: normalize })).toBeOnTheScreen();
    expect(within(card).getByText('توصيل')).toBeOnTheScreen();
    expect(within(card).getByText('استلام')).toBeOnTheScreen();
    expect(flatStyle(within(card).getByText('Anatolia Markt')).fontFamily).toBe('IBMPlexSansArabic-SemiBold');
    const logo = flatStyle(within(card).getByTestId('market-logo', { includeHiddenElements: true }));
    expect(logo).toMatchObject({ position: 'absolute', start: 12 });
    expect(logo).not.toHaveProperty('left');
  });
});
