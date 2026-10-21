import { screen, userEvent, waitFor, within } from '@testing-library/react-native';
import { I18nManager } from 'react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, inheritedDirection, renderApp } from '../../../test/render';
import { STORAGE_KEYS } from '../../../shared/storage';

describe('language switch from the globe button', () => {
  it('switches strings and direction instantly, persists the choice and refetches localised content', async () => {
    const forceRTL = jest.spyOn(I18nManager, 'forceRTL');
    const fake = createFakeApiClient({
      'GET /market-types': [{ code: 'BAKERY', name: 'Bäckerei' }],
      'GET /cuisines': [],
      'GET /markets': page([market()]),
    });
    const dependencies = createTestDependencies(fake.client);
    await renderApp(dependencies);
    const user = userEvent.setup();
    await screen.findByText('Bäckerei');
    const typeCalls = () => fake.calls.filter((c) => c.path === '/market-types').length;
    expect(typeCalls()).toBe(1);

    await user.press(screen.getByTestId('globe-button'));
    const sheet = await screen.findByTestId('language-sheet');
    expect(within(sheet).getByRole('button', { name: 'Deutsch' })).toBeSelected();
    await user.press(within(sheet).getByRole('button', { name: 'العربية' }));

    // Strings, direction and the stored preference change at once.
    expect(await screen.findByRole('button', { name: 'تغيير اللغة' })).toBeOnTheScreen();
    expect(inheritedDirection(screen.getByTestId('location-button'))).toBe('rtl');
    expect(await dependencies.storage.get(STORAGE_KEYS.language)).toBe('ar');
    expect(dependencies.languageStore.get()).toBe('ar');
    expect(forceRTL).toHaveBeenCalledWith(true);
    // The restart hint explains what only follows after a restart.
    expect(screen.getByText(/بعض عناصر النظام/)).toBeOnTheScreen();
    // Language is part of every query key, so localised lists are fetched again.
    await waitFor(() => expect(typeCalls()).toBe(2));
  });
});
