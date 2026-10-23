import { getPathFromState, getStateFromPath } from '@react-navigation/native';

import { SCREEN_PATHS } from '../linking';

const config = { screens: SCREEN_PATHS };

describe('screen paths', () => {
  it('opens a product of a market from its address and writes the same address back', () => {
    const state = getStateFromPath('/markets/m-1/products/p-9', config);
    expect(state?.routes[0]).toMatchObject({
      name: 'Market',
      params: { marketId: 'm-1' },
      state: { routes: [{ name: 'MarketProduct', params: { productId: 'p-9' } }] },
    });
    if (state === undefined) {
      throw new Error('no state');
    }
    expect(getPathFromState(state, config)).toBe('/markets/m-1/products/p-9');
  });

  it('keeps a product filter through the address bar', () => {
    const path = getPathFromState(
      {
        routes: [
          {
            name: 'Market',
            params: { marketId: 'm-1' },
            state: { routes: [{ name: 'MarketProducts', params: { title: 'Obst', filter: { categoryId: 'c-1', organicOnly: true } } }] },
          },
        ],
      },
      config,
    );
    const route = getStateFromPath(path, config)?.routes[0]?.state?.routes[0];
    expect(route).toMatchObject({ name: 'MarketProducts', params: { title: 'Obst', filter: { categoryId: 'c-1', organicOnly: true } } });
  });

  it('reads the reset link of the mail', () => {
    expect(getStateFromPath('/reset-password?token=abc', config)?.routes[0]).toMatchObject({
      name: 'ResetPassword',
      params: { token: 'abc' },
    });
  });
});
