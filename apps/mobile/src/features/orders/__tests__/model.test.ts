import { order } from '../../../test/fixtures';
import { canCancel, stepState, timelineSteps } from '../model';

describe('order timeline', () => {
  it('uses delivery or pickup steps and marks done/current/upcoming', () => {
    expect(timelineSteps('PICKUP')).toEqual(['PLACED', 'ACCEPTED', 'READY_FOR_PICKUP', 'COMPLETED']);
    const accepted = { status: 'ACCEPTED' as const, fulfilmentType: 'DELIVERY' as const };
    expect(timelineSteps('DELIVERY').map((s) => stepState(accepted, s))).toEqual(['done', 'current', 'upcoming', 'upcoming']);
    const completed = { status: 'COMPLETED' as const, fulfilmentType: 'DELIVERY' as const };
    expect(timelineSteps('DELIVERY').map((s) => stepState(completed, s))).toEqual(['done', 'done', 'done', 'done']);
  });

  it('allows cancelling PLACED or ACCEPTED orders before the cutoff only', () => {
    const before = new Date('2026-10-21T05:00:00+02:00');
    const after = new Date('2026-10-21T07:00:00+02:00');
    expect(canCancel(order(), before)).toBe(true);
    expect(canCancel(order({ status: 'ACCEPTED' }), before)).toBe(true);
    expect(canCancel(order(), after)).toBe(false);
    expect(canCancel(order({ status: 'OUT_FOR_DELIVERY' }), before)).toBe(false);
  });
});
