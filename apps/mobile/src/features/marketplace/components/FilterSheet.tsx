import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketSort } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, ListRow, Sheet, ToggleSwitch } from '../../../shared/ui';
import { DEFAULT_FILTERS, type MarketFilters } from '../model';

const SORTS = ['distance', 'minimum_order', 'delivery_fee'] as const satisfies readonly MarketSort[];
type ToggleKey = 'delivery' | 'pickup' | 'openNow' | 'hasOffers';
const TOGGLES: ToggleKey[] = ['delivery', 'pickup', 'openNow', 'hasOffers'];

/** Sort (distance, minimum order, delivery fee) and filters (delivery, pickup, open now, offers). */
export function FilterSheet({
  visible,
  filters,
  onApply,
  onClose,
}: {
  visible: boolean;
  filters: MarketFilters;
  onApply: (filters: MarketFilters) => void;
  onClose: () => void;
}) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  // The parent remounts the sheet (key) each time it opens, so the draft starts from the applied filters.
  const [draft, setDraft] = useState(filters);

  return (
    <Sheet
      visible={visible}
      onClose={onClose}
      title={t('discover.filterTitle')}
      testID="filter-sheet"
      footer={
        <View style={styles.footer}>
          <Button
            title={t('discover.reset')}
            variant="secondary"
            onPress={() => setDraft({ ...DEFAULT_FILTERS, types: draft.types, cuisines: draft.cuisines })}
            style={styles.flex}
          />
          <Button title={t('discover.apply')} onPress={() => onApply(draft)} style={styles.flex} testID="filter-apply" />
        </View>
      }>
      <AppText variant="subhead" color="textSecondary" accessibilityRole="header" style={styles.section}>
        {t('discover.sortBy')}
      </AppText>
      <View accessibilityRole="radiogroup">
        {SORTS.map((sort) => (
          <ListRow
            key={sort}
            title={t(`discover.sort.${sort}`)}
            chevron={false}
            selected={draft.sort === sort}
            onPress={() => setDraft((d) => ({ ...d, sort }))}
            trailing={draft.sort === sort ? <Icon name="check" color={colors.primary} /> : undefined}
            testID={`sort-${sort}`}
          />
        ))}
      </View>
      <AppText variant="subhead" color="textSecondary" accessibilityRole="header" style={styles.section}>
        {t('discover.filters')}
      </AppText>
      {TOGGLES.map((key) => (
        <View key={key} style={styles.toggleRow}>
          <AppText variant="body" dense style={styles.flex}>
            {t(`discover.filter.${key}`)}
          </AppText>
          <ToggleSwitch
            value={draft[key]}
            onValueChange={(value) => setDraft((d) => ({ ...d, [key]: value }))}
            accessibilityLabel={t(`discover.filter.${key}`)}
            testID={`filter-${key}`}
          />
        </View>
      ))}
    </Sheet>
  );
}

const useStyles = makeStyles((t) => ({
  section: { marginTop: t.spacing.lg, marginBottom: t.spacing.xs },
  toggleRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
  footer: { flexDirection: 'row', gap: t.spacing.md },
  flex: { flex: 1 },
}));
