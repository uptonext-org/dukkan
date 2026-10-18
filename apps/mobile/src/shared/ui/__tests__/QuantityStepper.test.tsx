import { render, screen, userEvent } from '@testing-library/react-native';

import { QuantityStepper } from '../QuantityStepper';

describe('<QuantityStepper />', () => {
  it('increments and decrements within bounds', async () => {
    const user = userEvent.setup();
    const onChange = jest.fn();
    await render(<QuantityStepper value={2} max={5} itemName="Apples" onChange={onChange} />);

    await user.press(screen.getByRole('button', { name: 'Increase quantity of Apples' }));
    expect(onChange).toHaveBeenLastCalledWith(3);

    await user.press(screen.getByRole('button', { name: 'Decrease quantity of Apples' }));
    expect(onChange).toHaveBeenLastCalledWith(1);

    expect(screen.getByLabelText('Quantity 2')).toBeOnTheScreen();
  });

  it('disables increase at the maximum and explains why', async () => {
    const user = userEvent.setup();
    const onChange = jest.fn();
    await render(<QuantityStepper value={24} max={24} itemName="Apples" onChange={onChange} />);

    const increase = screen.getByRole('button', { name: 'Increase quantity of Apples' });
    expect(increase).toBeDisabled();
    expect(increase.props.accessibilityHint).toBe('Maximum of 24 reached');
    await user.press(increase);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('labels the last decrement as removal and disables it at the minimum', async () => {
    const onChange = jest.fn();
    await render(<QuantityStepper value={1} max={10} itemName="Apples" onChange={onChange} />);
    expect(screen.getByRole('button', { name: 'Remove Apples' })).toBeEnabled();

    await screen.rerender(
      <QuantityStepper value={1} min={1} max={10} itemName="Apples" onChange={onChange} />,
    );
    expect(screen.getByRole('button', { name: 'Decrease quantity of Apples' })).toBeDisabled();
  });

  it('disables both buttons when disabled', async () => {
    await render(<QuantityStepper value={3} max={10} itemName="Apples" onChange={jest.fn()} disabled />);
    expect(screen.getByRole('button', { name: 'Increase quantity of Apples' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Decrease quantity of Apples' })).toBeDisabled();
  });
});
