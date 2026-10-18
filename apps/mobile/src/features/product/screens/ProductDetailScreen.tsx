import { StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../shared/api';
import { unitLabel } from '../../../shared/format/money';
import { colors, spacing, typography } from '../../../shared/theme';
import { Badge, Button, InitialsTile, Price, QuantityStepper, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useCartQuantities, useSetCartQuantity } from '../../cart/api/hooks';
import { useProduct } from '../api/hooks';

function Section({ title, children }: { title: string; children: string }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle} accessibilityRole="header">
        {title}
      </Text>
      <Text style={styles.body}>{children}</Text>
    </View>
  );
}

export function ProductDetailScreen({ route }: RootStackScreenProps<'ProductDetail'>) {
  const product = useProduct(route.params.productId);
  const quantities = useCartQuantities();
  const setQuantity = useSetCartQuantity();

  const state = queryState(product);
  if (state) {
    return <Screen>{state}</Screen>;
  }
  const p = product.data;
  if (p === undefined) {
    return null;
  }
  const quantity = quantities.get(p.id) ?? 0;
  const change = (next: number) => setQuantity.mutate({ productId: p.id, quantity: next });

  return (
    <Screen
      scroll
      footer={
        <View>
          {setQuantity.isError && (
            <Text style={styles.error} accessibilityRole="alert">
              {errorMessage(setQuantity.error)}
            </Text>
          )}
          {!p.available ? (
            <Badge label="Currently unavailable" tone="neutral" />
          ) : quantity === 0 ? (
            <Button
              title="Add to cart"
              onPress={() => change(1)}
              loading={setQuantity.isPending}
              accessibilityLabel={`Add ${p.name} to cart`}
            />
          ) : (
            <View style={styles.stepperRow}>
              <Text style={styles.body}>In your cart</Text>
              <QuantityStepper
                value={quantity}
                max={p.maxOrderQuantity}
                itemName={p.name}
                busy={setQuantity.isPending}
                onChange={change}
              />
            </View>
          )}
        </View>
      }>
      <View style={styles.hero}>
        <InitialsTile name={p.name} size={160} />
      </View>
      {p.promotion && (
        <Badge label={p.promotion.label} tone="promo" accessibilityLabel={`Offer: ${p.promotion.label}`} />
      )}
      <Text style={styles.title} accessibilityRole="header">
        {p.name}
      </Text>
      <Text style={styles.meta}>{[p.brand, p.unitSize].filter(Boolean).join(' · ')}</Text>
      <View style={styles.priceRow}>
        <Price money={p.price} size="large" accessibilityPrefix="Price" />
        {p.comparisonPrice && (
          <Text style={styles.meta}>
            <Price money={p.comparisonPrice.price} size="small" /> per {unitLabel(p.comparisonPrice.unit)}
          </Text>
        )}
      </View>
      {quantity >= p.maxOrderQuantity && (
        <Text style={styles.meta}>Maximum of {p.maxOrderQuantity} per order reached.</Text>
      )}
      {p.description != null && p.description !== '' && (
        <Section title="Description">{p.description}</Section>
      )}
      {p.ingredients != null && p.ingredients !== '' && (
        <Section title="Ingredients">{p.ingredients}</Section>
      )}
      <Section title="Allergens">
        {p.allergens.length > 0 ? p.allergens.join(', ') : 'No allergens declared.'}
      </Section>
      {p.origin != null && p.origin !== '' && <Section title="Origin">{p.origin}</Section>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { alignItems: 'center', marginBottom: spacing.lg },
  title: { ...typography.title, color: colors.text, marginTop: spacing.sm },
  meta: { ...typography.caption, color: colors.textMuted, marginTop: spacing.xs },
  priceRow: { marginTop: spacing.md, gap: spacing.xs },
  section: { marginTop: spacing.xl },
  sectionTitle: { ...typography.bodyStrong, color: colors.text, marginBottom: spacing.xs },
  body: { ...typography.body, color: colors.text },
  stepperRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  error: { ...typography.caption, color: colors.danger, marginBottom: spacing.sm },
});
