import { withFavorite } from '../api/hooks';

describe('withFavorite', () => {
  it('sets the flag on every copy of the product and keeps everything else identical', () => {
    const other = { id: 'p2', favorite: false };
    const data = { pages: [{ items: [{ id: 'p1', favorite: false, name: 'Ayran' }, other] }], pageParams: [0], market: { id: 'm1' } };
    const next = withFavorite(data, 'p1', true) as typeof data;
    expect(next.pages[0]?.items[0]).toEqual({ id: 'p1', favorite: true, name: 'Ayran' });
    expect(next.pages[0]?.items[1]).toBe(other);
    expect(next.market).toBe(data.market);
    expect(withFavorite(data, 'p9', true)).toBe(data);
  });
});
