import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { ShoppingListLine } from '../../../shared/api';
import { forSpeech } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Chip, Icon, ImageTile, placeholderProps, Price, QuantityStepper } from '../../../shared/ui';
import { choicesOf, type LineSelection } from '../model';

/** One line of a read shopping list: check box, chosen product, quantity, alternatives. */
export function ShoppingListLineRow({
  index,
  line,
  selection,
  onChange,
}: {
  index: number;
  line: ShoppingListLine;
  selection: LineSelection;
  onChange: (next: LineSelection) => void;
}) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const choices = choicesOf(line);
  const choice = choices[selection.choice];
  const said =
    line.amount !== null && !line.text.includes(line.amount) ? `${line.text} (${line.amount})` : line.text;

  if (choice === undefined) {
    return (
      <View style={styles.row} testID={`shopping-line-${index}`}>
        <Icon name="alert" size={22} color={colors.textSecondary} />
        <View style={styles.body}>
          <AppText variant="bodyStrong">{said}</AppText>
          <AppText variant="subhead" color="textSecondary">
            {t('shoppingList.lineNotFound')}
          </AppText>
        </View>
      </View>
    );
  }
  const { product } = choice;
  const price = product.promotionalPrice ?? product.price;
  return (
    <View style={[styles.row, !selection.selected && styles.off]} testID={`shopping-line-${index}`}>
      <Pressable
        onPress={() => onChange({ ...selection, selected: !selection.selected })}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: selection.selected }}
        accessibilityLabel={forSpeech(t('shoppingList.include', { name: product.name }))}
        hitSlop={8}
        style={[styles.check, selection.selected && styles.checkOn]}
        testID={`shopping-line-${index}-check`}>
        {selection.selected && <Icon name="check" size={16} color={colors.onAccent} />}
      </Pressable>
      <ImageTile uri={product.imageUrl} size={56} radius={12} {...placeholderProps(product.iconKey)} />
      <View style={styles.body}>
        <AppText variant="caption" color="textSecondary" numberOfLines={1}>
          {said}
        </AppText>
        <AppText variant="bodyStrong" numberOfLines={2}>
          {product.name}
        </AppText>
        <View style={styles.meta}>
          <AppText variant="subhead" color="textSecondary">
            {product.unitSize}
          </AppText>
          <Price money={price} variant="subhead" />
        </View>
        {choices.length > 1 && (
          <View style={styles.alternatives} accessibilityLabel={t('shoppingList.alternatives')}>
            {choices.map((c, i) => (
              <Chip
                key={c.product.id}
                label={c.product.name}
                selected={i === selection.choice}
                onPress={() => onChange({ selected: true, choice: i, quantity: c.quantity })}
                testID={`shopping-line-${index}-choice-${i}`}
              />
            ))}
          </View>
        )}
      </View>
      <QuantityStepper
        value={selection.quantity}
        onChange={(next) => onChange({ ...selection, selected: next > 0, quantity: Math.max(1, next) })}
        max={product.maxOrderQuantity}
        itemName={product.name}
        appearance="inline"
        disabled={!selection.selected}
      />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: t.spacing.sm, paddingVertical: t.spacing.sm },
  off: { opacity: 0.55 },
  body: { flex: 1, gap: 2 },
  meta: { flexDirection: 'row', gap: t.spacing.sm, alignItems: 'baseline' },
  alternatives: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.xs, marginTop: t.spacing.xs },
  check: {
    width: 26,
    height: 26,
    marginTop: 16,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: t.colors.controlTrackOff,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkOn: { backgroundColor: t.colors.primary, borderColor: t.colors.primary },
}));
