import { sizedImageUrl } from '../sizedImageUrl';

const KEY = `${'a'.repeat(64)}.jpg`;
const URL = `https://api.dukkan.app/api/v1/media/${KEY}`;

describe('sizedImageUrl (B-32d)', () => {
  it('asks for the smallest server copy that is wide enough', () => {
    expect(sizedImageUrl(URL, 100)).toBe(`${URL}?w=160`);
    expect(sizedImageUrl(URL, 160)).toBe(`${URL}?w=160`);
    expect(sizedImageUrl(URL, 161)).toBe(`${URL}?w=320`);
    expect(sizedImageUrl(URL, 700)).toBe(`${URL}?w=1080`);
    expect(sizedImageUrl(URL, 3000)).toBe(`${URL}?w=1080`);
  });

  it('leaves other URLs and odd sizes alone', () => {
    expect(sizedImageUrl('https://example.com/photo.jpg', 100)).toBe('https://example.com/photo.jpg');
    expect(sizedImageUrl(`${URL}?w=320`, 100)).toBe(`${URL}?w=320`);
    expect(sizedImageUrl(URL, 0)).toBe(URL);
    expect(sizedImageUrl(URL, Number.NaN)).toBe(URL);
  });
});
