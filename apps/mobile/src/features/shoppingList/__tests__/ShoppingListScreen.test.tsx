import { act, screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { ApiError, type ApiRequest, type ShoppingListResult } from '../../../shared/api';
import { createFakeShoppingInput } from '../../../shared/shoppingInput';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, cartItem, market, MARKET_ID, marketHome, page, product } from '../../../test/fixtures';
import { createTestDependencies, renderApp, type TestOptions } from '../../../test/render';

const CUCUMBER = product({ id: 'p-cucumber', name: 'Salatgurke', unitSize: '1 Stück', maxOrderQuantity: 10 });
const TOMATOES = product({
  id: 'p-tomatoes',
  name: 'Strauchtomaten',
  unitSize: '500 g',
  maxOrderQuantity: 10,
});
const CHERRY = product({ id: 'p-cherry', name: 'Kirschtomaten', unitSize: '250 g', maxOrderQuantity: 10 });

const RESULT: ShoppingListResult = {
  engine: 'RULES',
  lines: [
    { text: '2 Gurken', amount: null, match: { product: CUCUMBER, quantity: 2 }, alternatives: [] },
    {
      text: '1 kg Tomaten',
      amount: '1 kg',
      match: { product: TOMATOES, quantity: 2 },
      alternatives: [{ product: CHERRY, quantity: 4 }],
    },
    { text: 'Drachenfrucht', amount: null, match: null, alternatives: [] },
  ],
};

function setup(
  options: { ai: boolean; result?: ShoppingListResult | ((request: ApiRequest) => unknown); failTomatoesOnce?: boolean } & TestOptions,
) {
  const { ai, result, failTomatoesOnce = false, ...testOptions } = options;
  let tomatoFailures = failTomatoesOnce ? 1 : 0;
  const quantities: Record<string, number> = {};
  const fake = createFakeApiClient({
    'GET /market-types': [],
    'GET /cuisines': [],
    'GET /markets': page([market()]),
    'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
    'GET /me/notifications': { ...page([]), unreadCount: 0 },
    [`GET /markets/${MARKET_ID}`]: market(),
    [`GET /markets/${MARKET_ID}/home`]: marketHome(),
    'GET /favorites/markets': [],
    'GET /carts': [],
    // One cucumber is in the cart already.
    [`GET /markets/${MARKET_ID}/cart`]: cart({ items: [cartItem({ productId: CUCUMBER.id, quantity: 1 })] }),
    'GET /shopping-list': { ai, maxImages: 4, maxTextLength: 2000 },
    [`POST /markets/${MARKET_ID}/shopping-list`]: result ?? RESULT,
    [`PUT /markets/${MARKET_ID}/cart/items/${CUCUMBER.id}`]: (request: ApiRequest) => {
      quantities[CUCUMBER.id] = (request.body as { quantity: number }).quantity;
      return cart();
    },
    [`PUT /markets/${MARKET_ID}/cart/items/${TOMATOES.id}`]: (request: ApiRequest) => {
      if (tomatoFailures > 0) {
        tomatoFailures -= 1;
        throw new ApiError({ kind: 'http', status: 422, code: 'MAX_QUANTITY_EXCEEDED', detail: 'too many' });
      }
      quantities[TOMATOES.id] = (request.body as { quantity: number }).quantity;
      return cart();
    },
    [`PUT /markets/${MARKET_ID}/cart/items/${CHERRY.id}`]: (request: ApiRequest) => {
      quantities[CHERRY.id] = (request.body as { quantity: number }).quantity;
      return cart();
    },
  });
  return {
    fake,
    quantities,
    dependencies: createTestDependencies(fake.client, { token: 'jwt', ...testOptions }),
  };
}

async function openShoppingList() {
  const user = userEvent.setup();
  await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
  await user.press(await screen.findByTestId('shopping-list-entry'));
  await screen.findByTestId('shopping-list');
  return user;
}

/**
 * The multipart body's fields in order. Jest's (Node's) FormData turns React Native file parts
 * ({ uri, type, name }) into strings, so only their field names are meaningful here.
 */
function parts(request: ApiRequest | undefined): { name: string; value: unknown }[] {
  expect(request?.body).toBeInstanceOf(FormData);
  return [...(request?.body as FormData & { entries(): Iterable<[string, unknown]> }).entries()].map(
    ([name, value]) => ({ name, value }),
  );
}

describe('Einkaufsliste per Sprache, Foto oder Video', () => {
  it('matches a typed list without AI, explains the simple reading and fills the cart with the chosen lines', async () => {
    const { fake, quantities, dependencies } = setup({ ai: false });
    await renderApp(dependencies);
    const user = await openShoppingList();

    // Without a language model: a clear notice, and no photo or video buttons.
    expect(await screen.findByTestId('shopping-list-no-ai')).toHaveTextContent(
      /KI ist für diesen Dienst noch nicht eingerichtet/,
    );
    expect(screen.queryByTestId('shopping-list-video')).not.toBeOnTheScreen();

    await user.type(screen.getByLabelText('Deine Einkaufsliste'), '2 Gurken, 1 kg Tomaten, Drachenfrucht');
    await user.press(screen.getByTestId('shopping-list-recognise'));

    await screen.findByTestId('shopping-list-result');
    const request = fake.calls.find(
      (c) => c.method === 'POST' && c.path === `/markets/${MARKET_ID}/shopping-list`,
    );
    expect(parts(request)).toEqual([{ name: 'text', value: '2 Gurken, 1 kg Tomaten, Drachenfrucht' }]);
    expect(screen.getByText(/^.?2.? Artikel gefunden\. .?1.? nicht im Sortiment\.$/)).toBeOnTheScreen();
    expect(screen.getByTestId('shopping-list-rules-notice')).toHaveTextContent(
      /Ohne KI wird die Liste einfach gelesen/,
    );
    expect(screen.getByTestId('shopping-line-1')).toHaveTextContent(/Strauchtomaten/);
    expect(
      within(screen.getByTestId('shopping-line-2')).getByText('Nicht im Sortiment dieses Markts gefunden'),
    ).toBeOnTheScreen();

    // Cherry tomatoes instead (4 × 250 g), and the cucumbers on top of the one in the cart.
    await user.press(screen.getByTestId('shopping-line-1-choice-1'));
    await user.press(screen.getByRole('button', { name: '2 Artikel in den Warenkorb' }));

    expect(await screen.findByTestId('market-cart')).toBeOnTheScreen();
    expect(quantities).toEqual({ [CUCUMBER.id]: 3, [CHERRY.id]: 4 });
  });

  it('leaves out unchecked lines', async () => {
    const { quantities, dependencies } = setup({ ai: false });
    await renderApp(dependencies);
    const user = await openShoppingList();
    await user.type(screen.getByLabelText('Deine Einkaufsliste'), '2 Gurken, 1 kg Tomaten');
    await user.press(screen.getByTestId('shopping-list-recognise'));
    await screen.findByTestId('shopping-list-result');

    await user.press(screen.getByRole('checkbox', { name: 'Salatgurke in den Warenkorb legen' }));
    await user.press(screen.getByRole('button', { name: '1 Artikel in den Warenkorb' }));

    await screen.findByTestId('market-cart');
    expect(quantities).toEqual({ [TOMATOES.id]: 2 });
  });

  it('does not add a line twice when the cart update fails part-way', async () => {
    const { fake, quantities, dependencies } = setup({ ai: false, failTomatoesOnce: true });
    await renderApp(dependencies);
    const user = await openShoppingList();
    await user.type(screen.getByLabelText('Deine Einkaufsliste'), '2 Gurken, 1 kg Tomaten');
    await user.press(screen.getByTestId('shopping-list-recognise'));
    await screen.findByTestId('shopping-list-result');

    await user.press(await screen.findByRole('button', { name: '2 Artikel in den Warenkorb' }));
    // The cucumbers made it; the tomatoes did not. Only the tomatoes are left to add.
    expect(await screen.findByRole('checkbox', { name: 'Salatgurke in den Warenkorb legen', checked: false })).toBeOnTheScreen();
    await user.press(await screen.findByRole('button', { name: '1 Artikel in den Warenkorb' }));

    await screen.findByTestId('market-cart');
    const cucumberWrites = fake.calls.filter((c) => c.method === 'PUT' && c.path.endsWith(CUCUMBER.id));
    expect(cucumberWrites).toHaveLength(1);
    expect(quantities).toEqual({ [CUCUMBER.id]: 3, [TOMATOES.id]: 2 });
  });

  it('dictates on the device and sends the transcript with frames of a video', async () => {
    const speech = createFakeShoppingInput({
      videos: [
        {
          status: 'picked',
          frames: [1, 2, 3, 4].map((i) => ({
            uri: `file:///tmp/frame-${i}.jpg`,
            type: 'image/jpeg',
            fileName: `frame-${i}.jpg`,
          })),
        },
      ],
    });
    const { fake, dependencies } = setup({
      ai: true,
      result: { ...RESULT, engine: 'AI' },
      shoppingInput: speech.input,
      language: 'ar',
    });
    await renderApp(dependencies);
    const user = userEvent.setup();
    await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
    await user.press(await screen.findByTestId('shopping-list-entry'));
    await screen.findByTestId('shopping-list');

    await user.press(await screen.findByTestId('shopping-list-mic'));
    expect(speech.calls.languages).toEqual(['ar-SA']);
    expect(await screen.findByText('جارٍ الاستماع … اضغطوا عند الانتهاء')).toBeOnTheScreen();
    await act(() => {
      speech.say('خيارتين وكيلو طماطم');
      return Promise.resolve();
    });
    expect(screen.getByTestId('shopping-list-text')).toHaveDisplayValue('خيارتين وكيلو طماطم');
    await user.press(screen.getByTestId('shopping-list-mic'));
    expect(screen.getByTestId('shopping-list-text')).toHaveDisplayValue('خيارتين وكيلو طماطم');

    await user.press(screen.getByTestId('shopping-list-video'));
    expect(speech.calls.videoSources).toEqual(['camera']);
    expect(await screen.findByText(/^.?4.? صور مختارة$/)).toBeOnTheScreen();
    await user.press(screen.getByTestId('shopping-list-recognise'));

    await screen.findByTestId('shopping-list-result');
    const request = fake.calls.find(
      (c) => c.method === 'POST' && c.path === `/markets/${MARKET_ID}/shopping-list`,
    );
    expect(parts(request).map((p) => p.name)).toEqual(['text', 'images', 'images', 'images', 'images']);
    expect(screen.queryByTestId('shopping-list-rules-notice')).not.toBeOnTheScreen();
  });

  it('explains a missing microphone permission and a server without AI for photos', async () => {
    const speech = createFakeShoppingInput({ startError: 'permission' });
    const { dependencies } = setup({
      ai: true,
      shoppingInput: speech.input,
      result: () => {
        throw new ApiError({
          kind: 'http',
          status: 422,
          code: 'SHOPPING_LIST_AI_UNAVAILABLE',
          detail: 'not set up',
        });
      },
    });
    await renderApp(dependencies);
    const user = await openShoppingList();

    await user.press(await screen.findByTestId('shopping-list-mic'));
    expect(await screen.findByTestId('shopping-list-problem')).toHaveTextContent(
      /Mikrofon und die Spracherkennung/,
    );

    await user.type(screen.getByLabelText('Deine Einkaufsliste'), 'Brot');
    await user.press(screen.getByTestId('shopping-list-recognise'));
    await waitFor(() =>
      expect(screen.getByTestId('shopping-list-problem')).toHaveTextContent(
        /KI ist für diesen Dienst noch nicht eingerichtet/,
      ),
    );
  });
});
