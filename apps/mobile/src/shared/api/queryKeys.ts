import type { ListProductsParams } from './types';

export const queryKeys = {
  me: ['me'] as const,
  categories: ['categories'] as const,
  products: (params: Omit<ListProductsParams, 'page'>) => ['products', params] as const,
  product: (productId: string) => ['product', productId] as const,
  suggestions: (q: string) => ['suggestions', q] as const,
  promotions: ['promotions'] as const,
  cart: ['cart'] as const,
  addresses: ['addresses'] as const,
  deliveryArea: (postalCode: string) => ['deliveryArea', postalCode] as const,
  deliverySlots: (addressId: string) => ['deliverySlots', addressId] as const,
  orders: ['orders'] as const,
  order: (orderId: string) => ['order', orderId] as const,
};
