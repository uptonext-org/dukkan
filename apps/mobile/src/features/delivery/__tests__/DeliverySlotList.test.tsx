import { render, screen, userEvent } from '@testing-library/react-native';

import { eur, slot } from '../../../test/fixtures';
import { DeliverySlotList } from '../components/DeliverySlotList';
import { groupSlotsByDay } from '../model';

const slots = [
  slot('mon-08', '2026-10-19T08:00:00+02:00', '2026-10-19T09:00:00+02:00', { fee: eur(0) }),
  slot('mon-09', '2026-10-19T09:00:00+02:00', '2026-10-19T10:00:00+02:00', {
    available: false,
    remainingCapacity: 0,
  }),
  // 23:30 UTC on Monday is already Tuesday in Berlin.
  slot('tue-01', '2026-10-19T23:30:00Z', '2026-10-20T00:30:00Z', { fee: eur(199) }),
  slot('tue-10', '2026-10-20T10:00:00+02:00', '2026-10-20T11:00:00+02:00'),
];

describe('groupSlotsByDay', () => {
  it('groups by Europe/Berlin calendar day in start order', () => {
    const days = groupSlotsByDay([...slots].reverse());
    expect(days.map((d) => d.key)).toEqual(['2026-10-19', '2026-10-20']);
    expect(days[0]?.data.map((s) => s.id)).toEqual(['mon-08', 'mon-09']);
    expect(days[1]?.data.map((s) => s.id)).toEqual(['tue-01', 'tue-10']);
    expect(days[0]?.title).toBe('Montag, 19. Oktober');
  });

  it('flags fully booked days', () => {
    const days = groupSlotsByDay(slots.filter((s) => !s.available));
    expect(days[0]?.hasAvailability).toBe(false);
  });
});

describe('<DeliverySlotList />', () => {
  it('renders day headers, fees and disables unavailable slots', async () => {
    const onSelect = jest.fn();
    await render(<DeliverySlotList slots={slots} selectedSlotId="tue-10" onSelect={onSelect} />);

    expect(screen.getByRole('header', { name: 'Montag, 19. Oktober' })).toBeOnTheScreen();
    expect(screen.getByRole('header', { name: 'Dienstag, 20. Oktober' })).toBeOnTheScreen();

    expect(screen.getByRole('radio', { name: '08:00–09:00, free delivery, 5 left' })).toBeEnabled();
    const unavailable = screen.getByRole('radio', {
      name: '09:00–10:00, delivery fee 0,99 Euro, unavailable',
    });
    expect(unavailable).toBeDisabled();
    expect(screen.getByRole('radio', { name: '01:30–02:30, delivery fee 1,99 Euro, 5 left' })).toBeEnabled();
    expect(screen.getByRole('radio', { name: '10:00–11:00, delivery fee 0,99 Euro, 5 left' })).toBeChecked();

    const user = userEvent.setup();
    await user.press(unavailable);
    expect(onSelect).not.toHaveBeenCalled();
    await user.press(screen.getByRole('radio', { name: '08:00–09:00, free delivery, 5 left' }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'mon-08' }));
  });

  it('shows an empty state without slots', async () => {
    await render(<DeliverySlotList slots={[]} selectedSlotId={undefined} onSelect={jest.fn()} />);
    expect(screen.getByText('No delivery slots')).toBeOnTheScreen();
  });
});
