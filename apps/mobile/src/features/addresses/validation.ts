import type { Address, AddressRequest, GeoPoint } from '../../shared/api';
import type { FieldErrors } from '../auth/validation';

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
  location: GeoPoint | null;
}

export type AddressField = 'label' | 'street' | 'houseNumber' | 'addition' | 'postalCode' | 'city' | 'deliveryInstructions';
export type AddressErrors = FieldErrors<AddressField>;

export const EMPTY_ADDRESS: AddressFormValues = {
  label: '',
  street: '',
  houseNumber: '',
  addition: '',
  postalCode: '',
  city: 'Hamburg',
  deliveryInstructions: '',
  isDefault: false,
  location: null,
};

const LIMITS: Record<AddressField, { max: number; required: boolean }> = {
  label: { max: 50, required: false },
  street: { max: 120, required: true },
  houseNumber: { max: 10, required: true },
  addition: { max: 50, required: false },
  postalCode: { max: 5, required: true },
  city: { max: 80, required: true },
  deliveryInstructions: { max: 300, required: false },
};

export function validateAddress(values: AddressFormValues): AddressErrors {
  const errors: AddressErrors = {};
  for (const field of Object.keys(LIMITS) as AddressField[]) {
    const { max, required } = LIMITS[field];
    const value = values[field].trim();
    if (required && value === '') {
      errors[field] = 'required';
    } else if (value.length > max) {
      errors[field] = 'tooLong';
    }
  }
  if (errors.postalCode === undefined && !POSTAL_CODE_PATTERN.test(values.postalCode.trim())) {
    errors.postalCode = 'postalCode';
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
    location: values.location,
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
    location: address.location ?? null,
  };
}

export function formatAddressLine(address: Pick<Address, 'street' | 'houseNumber' | 'postalCode' | 'city'>): string {
  return `${address.street} ${address.houseNumber}, ${address.postalCode} ${address.city}`;
}
