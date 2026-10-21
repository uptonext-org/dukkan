import { screen, userEvent, waitFor } from '@testing-library/react-native';

import type { Place } from '../../../shared/api';
import { LocationError } from '../../../shared/location';
import { STORAGE_KEYS } from '../../../shared/storage';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const langeReihe: Place = {
  label: 'Lange Reihe 12, 20099 Hamburg (St. Georg)',
  street: 'Lange Reihe',
  houseNumber: '12',
  postalCode: '20099',
  district: 'St. Georg',
  city: 'Hamburg',
  location: { lat: 53.5552, lng: 10.0105 },
};

function setup(getCurrentPosition: () => Promise<{ lat: number; lng: number }>) {
  const fake = createFakeApiClient({
    'GET /geo/search': [langeReihe],
    'GET /geo/reverse': langeReihe,
    'GET /market-types': [{ code: 'BAKERY', name: 'Bäckerei' }],
    'GET /cuisines': [],
    'GET /markets': page([market()]),
  });
  const dependencies = createTestDependencies(fake.client, { place: null, location: { getCurrentPosition } });
  return { fake, dependencies };
}

describe('Onboarding "Wo wohnst du?"', () => {
  it('explains why before asking, and stays usable through search when permission is denied', async () => {
    const getCurrentPosition = jest.fn(() => Promise.reject(new LocationError('denied')));
    const { fake, dependencies } = setup(getCurrentPosition);
    await renderApp(dependencies);
    const user = userEvent.setup();

    expect(await screen.findByRole('header', { name: 'Wo wohnst du?' })).toBeOnTheScreen();
    expect(screen.getByText('Damit wir dir Märkte in deiner Nähe zeigen können.')).toBeOnTheScreen();
    // No system prompt before the person asks for it.
    expect(getCurrentPosition).not.toHaveBeenCalled();
    // The globe is visible from the first screen (decision 23).
    expect(screen.getByRole('button', { name: 'Sprache ändern' })).toBeOnTheScreen();

    await user.press(screen.getByRole('button', { name: 'Standort verwenden' }));
    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId('location-denied')).toHaveTextContent(/Such einfach nach deiner Adresse/);
    // Map alternative is still offered.
    expect(screen.getByRole('button', { name: 'Auf der Karte wählen' })).toBeEnabled();

    await user.type(screen.getByTestId('place-search'), 'Lange');
    await user.press(await screen.findByRole('button', { name: langeReihe.label }));

    // Place stored on the device and the marketplace opens centred on it.
    expect(await screen.findByTestId('location-button')).toHaveTextContent('St. Georg');
    await waitFor(() =>
      expect(fake.calls).toContainEqual(expect.objectContaining({ path: '/markets', query: expect.objectContaining({ lat: 53.5552, lng: 10.0105 }) })),
    );
    expect(JSON.parse((await dependencies.storage.get(STORAGE_KEYS.place)) ?? '{}')).toMatchObject({ shortLabel: 'St. Georg' });
    expect(fake.calls).toContainEqual(expect.objectContaining({ path: '/geo/search', query: expect.objectContaining({ q: 'Lange' }) }));
  });

  it('uses the device position and reverse geocodes it', async () => {
    const { fake, dependencies } = setup(() => Promise.resolve({ lat: 53.55, lng: 10.01 }));
    await renderApp(dependencies);
    const user = userEvent.setup();
    await user.press(await screen.findByRole('button', { name: 'Standort verwenden' }));
    expect(await screen.findByTestId('location-button')).toHaveTextContent('St. Georg');
    expect(fake.calls).toContainEqual(expect.objectContaining({ path: '/geo/reverse', query: { lat: 53.55, lng: 10.01 } }));
  });
});
