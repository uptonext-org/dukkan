import type { Address, AddressRequest } from '../../shared/api';

export const POSTAL_CODE_PATTERN = /^[0-9]{5}$/;

export interface AddressFormValues {
  label: string;
  street: string;
  houseNumber: string;
  addition: string;
  postalCode: string;
  city: string;
  deliveryInstructions: string;
  isDefault: boolean;
}

export type AddressField = Exclude<keyof AddressFormValues, 'isDefault'>;
export type AddressErrors = { [K in AddressField]?: string | undefined };

export const EMPTY_ADDRESS: AddressFormValues = {
  label: '',
  street: '',
  houseNumber: '',
  addition: '',
  postalCode: '',
  city: '',
  deliveryInstructions: '',
  isDefault: false,
};

const LIMITS: Record<AddressField, { max: number; required: boolean; name: string }> = {
  label: { max: 50, required: false, name: 'Label' },
  street: { max: 120, required: true, name: 'Street' },
  houseNumber: { max: 10, required: true, name: 'House number' },
  addition: { max: 50, required: false, name: 'Address addition' },
  postalCode: { max: 5, required: true, name: 'Postal code' },
  city: { max: 80, required: true, name: 'City' },
  deliveryInstructions: { max: 300, required: false, name: 'Delivery instructions' },
};

export function validatePostalCode(postalCode: string): string | undefined {
  const value = postalCode.trim();
  if (value === '') {
    return 'Please enter a postal code.';
  }
  return POSTAL_CODE_PATTERN.test(value) ? undefined : 'Postal codes have exactly 5 digits.';
}

export function validateAddress(values: AddressFormValues): AddressErrors {
  const errors: AddressErrors = {};
  for (const field of Object.keys(LIMITS) as AddressField[]) {
    const { max, required, name } = LIMITS[field];
    const value = values[field].trim();
    if (required && value === '') {
      errors[field] = `${name} is required.`;
    } else if (value.length > max) {
      errors[field] = `${name} must be at most ${max} characters.`;
    }
  }
  const postal = validatePostalCode(values.postalCode);
  if (postal !== undefined) {
    errors.postalCode = postal;
  }
  return errors;
}

const optional = (value: string): string | null => (value.trim() === '' ? null : value.trim());

export function toAddressRequest(values: AddressFormValues): AddressRequest {
  return {
    label: optional(values.label),
    street: values.street.trim(),
    houseNumber: values.houseNumber.trim(),
    addition: optional(values.addition),
    postalCode: values.postalCode.trim(),
    city: values.city.trim(),
    deliveryInstructions: optional(values.deliveryInstructions),
    isDefault: values.isDefault,
  };
}

export function toFormValues(address: Address): AddressFormValues {
  return {
    label: address.label ?? '',
    street: address.street,
    houseNumber: address.houseNumber,
    addition: address.addition ?? '',
    postalCode: address.postalCode,
    city: address.city,
    deliveryInstructions: address.deliveryInstructions ?? '',
    isDefault: address.isDefault,
  };
}

export function formatAddressLine(
  address: Pick<Address, 'street' | 'houseNumber' | 'postalCode' | 'city'>,
): string {
  return `${address.street} ${address.houseNumber}, ${address.postalCode} ${address.city}`;
}
