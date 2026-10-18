import { memo, useCallback, useMemo } from 'react';
import { Pressable, SectionList, StyleSheet, Text, View, type SectionListRenderItem } from 'react-native';

import type { DeliverySlot } from '../../../shared/api';
import { formatTimeRange } from '../../../shared/format/dates';
import { formatMoneyForAccessibility } from '../../../shared/format/money';
import { colors, radii, spacing, touchTarget, typography } from '../../../shared/theme';
import { EmptyState, Price } from '../../../shared/ui';
import { groupSlotsByDay, type SlotDay } from '../model';

interface SlotRowProps {
  slot: DeliverySlot;
  selected: boolean;
  disabled: boolean;
  onSelect: (slot: DeliverySlot) => void;
}

const SlotRow = memo(function SlotRow({ slot, selected, disabled, onSelect }: SlotRowProps) {
  const time = formatTimeRange(slot.startsAt, slot.endsAt);
  const free = slot.fee.amountCents === 0;
  const unavailable = !slot.available;
  const label = [
    time,
    free ? 'free delivery' : `delivery fee ${formatMoneyForAccessibility(slot.fee)}`,
    unavailable ? 'unavailable' : `${slot.remainingCapacity} left`,
  ].join(', ');
  return (
    <Pressable
      onPress={() => onSelect(slot)}
      disabled={unavailable || disabled}
      accessibilityRole="radio"
      accessibilityLabel={label}
      accessibilityState={{ disabled: unavailable || disabled, checked: selected }}
      style={({ pressed }) => [
        styles.slot,
        selected && styles.selected,
        unavailable && styles.unavailable,
        pressed && styles.pressed,
      ]}>
      <View style={styles.slotMain}>
        <Text style={[styles.time, unavailable && styles.mutedText]}>{time}</Text>
        <Text style={styles.capacity}>{unavailable ? 'Unavailable' : `${slot.remainingCapacity} left`}</Text>
      </View>
      {free ? (
        <Text style={[styles.free, unavailable && styles.mutedText]}>Free</Text>
      ) : (
        <Price money={slot.fee} />
      )}
      {selected && <Text style={styles.selectedText}>Reserved</Text>}
    </Pressable>
  );
});

interface DeliverySlotListProps {
  slots: readonly DeliverySlot[];
  selectedSlotId: string | undefined;
  onSelect: (slot: DeliverySlot) => void;
  /** Disables all rows, e.g. while a reservation request is in flight. */
  disabled?: boolean;
}

export function DeliverySlotList({
  slots,
  selectedSlotId,
  onSelect,
  disabled = false,
}: DeliverySlotListProps) {
  const sections = useMemo(() => groupSlotsByDay(slots), [slots]);
  const renderItem = useCallback<SectionListRenderItem<DeliverySlot, SlotDay>>(
    ({ item }) => (
      <SlotRow slot={item} selected={item.id === selectedSlotId} disabled={disabled} onSelect={onSelect} />
    ),
    [selectedSlotId, disabled, onSelect],
  );
  return (
    <SectionList
      sections={sections}
      keyExtractor={(slot) => slot.id}
      renderItem={renderItem}
      renderSectionHeader={({ section }) => (
        <Text style={styles.day} accessibilityRole="header">
          {section.title}
          {section.hasAvailability ? '' : ' (fully booked)'}
        </Text>
      )}
      stickySectionHeadersEnabled
      contentContainerStyle={styles.content}
      ListEmptyComponent={
        <EmptyState title="No delivery slots" message="There are no slots in the next 7 days." />
      }
    />
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xl, flexGrow: 1 },
  day: {
    ...typography.bodyStrong,
    color: colors.text,
    backgroundColor: colors.background,
    paddingTop: spacing.lg,
    paddingBottom: spacing.sm,
  },
  slot: {
    minHeight: touchTarget + 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  selected: { borderColor: colors.primary, borderWidth: 2, backgroundColor: colors.primaryMuted },
  unavailable: { backgroundColor: colors.surfaceAlt },
  pressed: { backgroundColor: colors.primaryMuted },
  slotMain: { flex: 1 },
  time: { ...typography.bodyStrong, color: colors.text },
  capacity: { ...typography.caption, color: colors.textMuted },
  mutedText: { color: colors.textMuted },
  free: { ...typography.bodyStrong, color: colors.primary },
  selectedText: { ...typography.caption, color: colors.primary, fontWeight: '700' },
});
