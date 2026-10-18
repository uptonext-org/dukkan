import { screen, userEvent } from '@testing-library/react-native';

import { ApiError } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const user = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'Lovelace' };
const emptyPage = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };

describe('<LoginScreen />', () => {
  it('validates the form before calling the API', async () => {
    const fake = createFakeApiClient({});
    await renderApp(createTestDependencies(fake.client));
    const u = userEvent.setup();

    await u.press(await screen.findByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Please enter your email address.')).toBeOnTheScreen();
    expect(screen.getByText('Please enter your password.')).toBeOnTheScreen();

    await u.type(screen.getByLabelText('Email'), 'not-an-email');
    await u.press(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Please enter a valid email address.')).toBeOnTheScreen();
    expect(fake.calls).toHaveLength(0);
  });

  it('submits credentials, stores the token and shows the shop', async () => {
    const fake = createFakeApiClient({
      'POST /auth/login': { accessToken: 'jwt-abc', tokenType: 'Bearer', expiresIn: 3600, user },
      'GET /categories': [],
      'GET /products': emptyPage,
      'GET /cart': cart({ items: [], itemCount: 0 }),
    });
    const dependencies = createTestDependencies(fake.client);
    await renderApp(dependencies);
    const u = userEvent.setup();

    await u.type(await screen.findByLabelText('Email'), ' ada@example.de ');
    await u.type(screen.getByLabelText('Password'), 'correct horse');
    await u.press(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('button', { name: /^Shop, tab/ })).toBeOnTheScreen();
    expect(fake.calls[0]).toMatchObject({
      method: 'POST',
      path: '/auth/login',
      body: { email: 'ada@example.de', password: 'correct horse' },
    });
    expect(dependencies.session.getToken()).toBe('jwt-abc');
  });

  it('shows the server error for invalid credentials', async () => {
    const fake = createFakeApiClient({
      'POST /auth/login': () => {
        throw new ApiError({ kind: 'http', status: 401, code: 'INVALID_CREDENTIALS' });
      },
    });
    await renderApp(createTestDependencies(fake.client));
    const u = userEvent.setup();

    await u.type(await screen.findByLabelText('Email'), 'ada@example.de');
    await u.type(screen.getByLabelText('Password'), 'wrong');
    await u.press(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Email or password is incorrect.')).toBeOnTheScreen();
  });
});
