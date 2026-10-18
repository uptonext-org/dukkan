import { screen, userEvent } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { createTestDependencies, renderWithProviders } from '../../../test/render';
import { AddressForm, NOT_SERVICEABLE_MESSAGE } from '../components/AddressForm';
import { EMPTY_ADDRESS, toAddressRequest, validateAddress, validatePostalCode } from '../validation';

function setup(serviceable: Record<string, boolean>) {
  const fake = createFakeApiClient({
    'GET /delivery-areas/check': (req) => {
      const postalCode = String(req.query?.postalCode);
      return {
        postalCode,
        serviceable: serviceable[postalCode] ?? false,
        hubName: serviceable[postalCode] === true ? 'Hub Nord' : null,
      };
    },
  });
  return { fake, dependencies: createTestDependencies(fake.client, 'token') };
}

describe('<AddressForm />', () => {
  it('shows validation errors and does not submit', async () => {
    const { dependencies } = setup({});
    const onSubmit = jest.fn();
    await renderWithProviders(
      <AddressForm
        initialValues={EMPTY_ADDRESS}
        submitLabel="Save address"
        submitting={false}
        onSubmit={onSubmit}
      />,
      dependencies,
    );
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Postal code'), '1234');
    await user.press(screen.getByRole('button', { name: 'Save address' }));

    expect(screen.getByText('Street is required.')).toBeOnTheScreen();
    expect(screen.getByText('House number is required.')).toBeOnTheScreen();
    expect(screen.getByText('City is required.')).toBeOnTheScreen();
    expect(screen.getByText('Postal codes have exactly 5 digits.')).toBeOnTheScreen();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('pre-checks the postal code on blur and warns when not serviceable', async () => {
    const { fake, dependencies } = setup({ '41460': true });
    await renderWithProviders(
      <AddressForm
        initialValues={EMPTY_ADDRESS}
        submitLabel="Save address"
        submitting={false}
        onSubmit={jest.fn()}
      />,
      dependencies,
    );
    const user = userEvent.setup();

    // user.type focuses and blurs the field, triggering the pre-check.
    await user.type(screen.getByLabelText('Postal code'), '99999');
    expect(await screen.findByText(NOT_SERVICEABLE_MESSAGE)).toBeOnTheScreen();
    expect(fake.calls).toContainEqual(
      expect.objectContaining({ path: '/delivery-areas/check', query: { postalCode: '99999' } }),
    );

    await user.clear(screen.getByLabelText('Postal code'));
    await user.type(screen.getByLabelText('Postal code'), '41460');
    expect(await screen.findByText('Great, we deliver to 41460 from Hub Nord.')).toBeOnTheScreen();
  });

  it('submits trimmed values when valid', async () => {
    const { dependencies } = setup({ '41460': true });
    const onSubmit = jest.fn();
    await renderWithProviders(
      <AddressForm
        initialValues={EMPTY_ADDRESS}
        submitLabel="Save address"
        submitting={false}
        onSubmit={onSubmit}
      />,
      dependencies,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Street'), ' Hauptstraße ');
    await user.type(screen.getByLabelText('House number'), '12a');
    await user.type(screen.getByLabelText('Postal code'), '41460');
    await user.type(screen.getByLabelText('City'), 'Neuss');
    await user.press(screen.getByRole('button', { name: 'Save address' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const firstCall = onSubmit.mock.calls[0] as [typeof EMPTY_ADDRESS];
    expect(toAddressRequest(firstCall[0])).toEqual({
      label: null,
      street: 'Hauptstraße',
      houseNumber: '12a',
      addition: null,
      postalCode: '41460',
      city: 'Neuss',
      deliveryInstructions: null,
      isDefault: false,
    });
  });

  it('shows server-side field errors', async () => {
    const { dependencies } = setup({});
    await renderWithProviders(
      <AddressForm
        initialValues={EMPTY_ADDRESS}
        submitLabel="Save address"
        submitting={false}
        serverErrors={{ street: 'must not be blank' }}
        onSubmit={jest.fn()}
      />,
      dependencies,
    );
    expect(screen.getByText('must not be blank')).toBeOnTheScreen();
  });
});

describe('address validation', () => {
  it('validates postal codes with ^[0-9]{5}$', () => {
    expect(validatePostalCode('41460')).toBeUndefined();
    expect(validatePostalCode('4146')).toBeDefined();
    expect(validatePostalCode('414600')).toBeDefined();
    expect(validatePostalCode('4146a')).toBeDefined();
    expect(validatePostalCode('')).toBe('Please enter a postal code.');
  });

  it('enforces contract length limits', () => {
    const errors = validateAddress({
      ...EMPTY_ADDRESS,
      street: 'x'.repeat(121),
      houseNumber: '1',
      postalCode: '41460',
      city: 'Neuss',
    });
    expect(errors.street).toBe('Street must be at most 120 characters.');
  });
});
