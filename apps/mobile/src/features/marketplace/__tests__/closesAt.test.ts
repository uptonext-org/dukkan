import { closesAt } from '../format';

// 2026-11-02 is a Monday in winter time: Berlin = UTC+1.
const at = (berlin: string) => new Date(`2026-11-02T${berlin}:00+01:00`);

describe('closesAt: the closing time of the interval open now', () => {
  it('takes the current interval of split hours, not the last one of the day', () => {
    expect(closesAt('08:00–13:00, 14:00–18:30', at('10:00'))).toBe('13:00');
    expect(closesAt('08:00–13:00, 14:00–18:30', at('15:00'))).toBe('18:30');
  });

  it('handles an overnight interval and the hour after midnight', () => {
    expect(closesAt('20:00–02:00', at('22:00'))).toBe('02:00');
    // After midnight the open interval is yesterday's; today's hours do not tell when it closes.
    expect(closesAt('20:00–02:00', at('01:00'))).toBeUndefined();
  });

  it('is undefined without hours', () => {
    expect(closesAt(null, at('10:00'))).toBeUndefined();
  });
});
