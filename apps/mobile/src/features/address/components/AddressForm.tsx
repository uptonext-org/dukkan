import { useState } from 'react';
import { StyleSheet, Switch, Text, View } from 'react-native';

import { colors, radii, spacing, touchTarget, typography } from '../../../shared/theme';
import { Button, TextField } from '../../../shared/ui';
import { useDeliveryAreaCheck } from '../api/hooks';
import {
  validateAddress,
  validatePostalCode,
  type AddressErrors,
  type AddressField,
  type AddressFormValues,
} from '../validation';

export const NOT_SERVICEABLE_MESSAGE = "We don't deliver here yet.";

interface AddressFormProps {
  initialValues: AddressFormValues;
  submitLabel: string;
  submitting: boolean;
  /** Field errors returned by the server (problem+json `errors`). */
  serverErrors?: Readonly<Record<string, string>>;
  formError?: string | null;
  onSubmit: (values: AddressFormValues) => void;
}

export function AddressForm({
  initialValues,
  submitLabel,
  submitting,
  serverErrors,
  formError,
  onSubmit,
}: AddressFormProps) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState<AddressErrors>({});
  const [checkedPostalCode, setCheckedPostalCode] = useState<string | null>(
    initialValues.postalCode === '' ? null : initialValues.postalCode,
  );
  const area = useDeliveryAreaCheck(checkedPostalCode);

  const fieldError = (field: AddressField) => errors[field] ?? serverErrors?.[field];

  const update = (field: AddressField) => (text: string) => {
    setValues((prev) => ({ ...prev, [field]: text }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const onPostalCodeBlur = () => {
    const code = values.postalCode.trim();
    const error = validatePostalCode(code);
    setErrors((prev) => ({ ...prev, postalCode: error }));
    setCheckedPostalCode(error === undefined ? code : null);
  };

  const submit = () => {
    const validation = validateAddress(values);
    setErrors(validation);
    if (Object.values(validation).every((message) => message === undefined)) {
      onSubmit(values);
    }
  };

  const serviceability =
    checkedPostalCode !== null && area.data?.postalCode === checkedPostalCode ? area.data : undefined;

  return (
    <View>
      <TextField
        label="Label (optional)"
        value={values.label}
        onChangeText={update('label')}
        error={fieldError('label')}
        placeholder="Home"
        maxLength={50}
      />
      <TextField
        label="Street"
        value={values.street}
        onChangeText={update('street')}
        error={fieldError('street')}
        autoComplete="street-address"
        textContentType="streetAddressLine1"
        maxLength={120}
      />
      <TextField
        label="House number"
        value={values.houseNumber}
        onChangeText={update('houseNumber')}
        error={fieldError('houseNumber')}
        maxLength={10}
      />
      <TextField
        label="Address addition (optional)"
        value={values.addition}
        onChangeText={update('addition')}
        error={fieldError('addition')}
        hint="Floor, apartment, c/o"
        maxLength={50}
      />
      <TextField
        label="Postal code"
        value={values.postalCode}
        onChangeText={update('postalCode')}
        onBlur={onPostalCodeBlur}
        error={fieldError('postalCode')}
        keyboardType="number-pad"
        autoComplete="postal-code"
        textContentType="postalCode"
        maxLength={5}
      />
      {serviceability !== undefined && (
        <View
          style={[styles.notice, serviceability.serviceable ? styles.ok : styles.warn]}
          accessibilityRole="alert"
          accessibilityLiveRegion="polite">
          <Text style={serviceability.serviceable ? styles.okText : styles.warnText}>
            {serviceability.serviceable
              ? `Great, we deliver to ${serviceability.postalCode}${
                  serviceability.hubName != null ? ` from ${serviceability.hubName}` : ''
                }.`
              : NOT_SERVICEABLE_MESSAGE}
          </Text>
        </View>
      )}
      <TextField
        label="City"
        value={values.city}
        onChangeText={update('city')}
        error={fieldError('city')}
        textContentType="addressCity"
        maxLength={80}
      />
      <TextField
        label="Delivery instructions (optional)"
        value={values.deliveryInstructions}
        onChangeText={update('deliveryInstructions')}
        error={fieldError('deliveryInstructions')}
        multiline
        maxLength={300}
      />
      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>Use as default address</Text>
        <Switch
          value={values.isDefault}
          onValueChange={(isDefault) => setValues((prev) => ({ ...prev, isDefault }))}
          accessibilityLabel="Use as default address"
          accessibilityRole="switch"
          trackColor={{ true: colors.primary, false: colors.border }}
        />
      </View>
      {formError != null && (
        <Text style={styles.formError} accessibilityRole="alert">
          {formError}
        </Text>
      )}
      <Button title={submitLabel} onPress={submit} loading={submitting} />
    </View>
  );
}

const styles = StyleSheet.create({
  notice: { padding: spacing.md, borderRadius: radii.md, marginTop: -spacing.sm, marginBottom: spacing.lg },
  ok: { backgroundColor: colors.primaryMuted },
  warn: { backgroundColor: colors.warningMuted },
  okText: { ...typography.body, color: colors.primary },
  warnText: { ...typography.bodyStrong, color: colors.warning },
  switchRow: {
    minHeight: touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.lg,
  },
  switchLabel: { ...typography.body, color: colors.text, flex: 1 },
  formError: { ...typography.body, color: colors.danger, marginBottom: spacing.md },
});
