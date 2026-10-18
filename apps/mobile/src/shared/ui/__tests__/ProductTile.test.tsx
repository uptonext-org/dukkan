import { render, screen, userEvent } from '@testing-library/react-native';

import { product } from '../../../test/fixtures';
import { initialsOf } from '../InitialsTile';
import { ProductTile } from '../ProductTile';

describe('<ProductTile />', () => {
  it('shows name, price, promotion and an initials placeholder', async () => {
    await render(
      <ProductTile
        product={product({ promotion: { promotionId: 'promo-1', label: '-20%', type: 'PERCENT_OFF' } })}
        quantity={0}
        onPress={jest.fn()}
        onChangeQuantity={jest.fn()}
      />,
    );
    expect(screen.getByText('Oat Drink Barista')).toBeOnTheScreen();
    expect(screen.getByText('-20%')).toBeOnTheScreen();
    // Decorative placeholder: hidden from screen readers, so opt in to hidden elements.
    expect(screen.getByText('OD', { includeHiddenElements: true })).toBeOnTheScreen();
    expect(
      screen.getByRole('button', { name: 'Oat Drink Barista, Feldgut, 1 l, 2,49 Euro, Offer -20%' }),
    ).toBeOnTheScreen();
  });

  it('adds to cart and opens details', async () => {
    const user = userEvent.setup();
    const onPress = jest.fn();
    const onChangeQuantity = jest.fn();
    await render(
      <ProductTile product={product()} quantity={0} onPress={onPress} onChangeQuantity={onChangeQuantity} />,
    );

    await user.press(screen.getByRole('button', { name: 'Add Oat Drink Barista to cart' }));
    expect(onChangeQuantity).toHaveBeenCalledWith(product().id, 1);

    await user.press(screen.getByRole('button', { name: /^Oat Drink Barista,/ }));
    expect(onPress).toHaveBeenCalledWith(product().id);
  });

  it('shows a stepper when the product is in the cart', async () => {
    await render(
      <ProductTile product={product()} quantity={3} onPress={jest.fn()} onChangeQuantity={jest.fn()} />,
    );
    expect(screen.getByLabelText('Quantity 3')).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: 'Add Oat Drink Barista to cart' })).not.toBeOnTheScreen();
  });

  it('marks unavailable products', async () => {
    await render(
      <ProductTile
        product={product({ available: false })}
        quantity={0}
        onPress={jest.fn()}
        onChangeQuantity={jest.fn()}
      />,
    );
    expect(screen.getByText('Unavailable')).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: 'Add Oat Drink Barista to cart' })).not.toBeOnTheScreen();
  });
});

describe('initialsOf', () => {
  it('uses up to two initials', () => {
    expect(initialsOf('organic whole milk')).toBe('OW');
    expect(initialsOf('  Bananas ')).toBe('B');
    expect(initialsOf('')).toBe('?');
  });
});
