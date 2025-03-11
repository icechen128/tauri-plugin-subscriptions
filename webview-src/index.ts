import { invoke } from '@tauri-apps/api/tauri';

export enum ProductType {
  Consumable = 'Consumable',
  NonConsumable = 'NonConsumable',
  Subscription = 'Subscription'
}

export enum SubscriptionPeriod {
  Day = 'Day',
  Week = 'Week',
  Month = 'Month',
  Year = 'Year'
}

export interface Product {
  id: string;
  title: string;
  description: string;
  price: string;
  priceAmount: number;
  currencyCode: string;
  productType: ProductType;
  subscriptionPeriod?: SubscriptionPeriod;
  subscriptionPeriodUnit?: number;
}

export interface PurchaseResult {
  productId: string;
  transactionId: string;
  purchaseTime: number;
  isAcknowledged: boolean;
  subscriptionExpiryTime?: number;
  receiptData?: string;
}

export interface SubscriptionStatus {
  productId: string;
  isActive: boolean;
  expiryDate?: number;
  autoRenewStatus: boolean;
  isInTrialPeriod: boolean;
  isInGracePeriod: boolean;
}

/**
 * Get a list of products from the store
 * @param productIds Array of product IDs to retrieve
 * @returns Promise that resolves to an array of Product objects
 */
export async function getProducts(productIds: string[]): Promise<Product[]> {
  return await invoke<Product[]>('plugin:subscriptions|get_products', {
    productIds,
  });
}

/**
 * Purchase a product
 * @param productId ID of the product to purchase
 * @returns Promise that resolves to the PurchaseResult
 */
export async function purchaseProduct(productId: string): Promise<PurchaseResult> {
  return await invoke<PurchaseResult>('plugin:subscriptions|purchase_product', {
    productId,
  });
}

/**
 * Restore previously purchased products
 * @returns Promise that resolves to an array of PurchaseResult objects
 */
export async function restorePurchases(): Promise<PurchaseResult[]> {
  return await invoke<PurchaseResult[]>('plugin:subscriptions|restore_purchases');
}

/**
 * Get the status of a subscription
 * @param productId ID of the subscription product
 * @returns Promise that resolves to the SubscriptionStatus
 */
export async function getSubscriptionStatus(productId: string): Promise<SubscriptionStatus> {
  return await invoke<SubscriptionStatus>('plugin:subscriptions|get_subscription_status', {
    productId,
  });
}

/**
 * Check if a subscription is active
 * @param productId ID of the subscription product
 * @returns Promise that resolves to a boolean indicating if the subscription is active
 */
export async function isSubscriptionActive(productId: string): Promise<boolean> {
  const status = await getSubscriptionStatus(productId);
  return status.isActive;
}

/**
 * Get the expiration date of a subscription
 * @param productId ID of the subscription product
 * @returns Promise that resolves to the expiration date in milliseconds since epoch, or null if not available
 */
export async function getSubscriptionExpiryDate(productId: string): Promise<number | null> {
  const status = await getSubscriptionStatus(productId);
  return status.expiryDate || null;
}

/**
 * Format a price string with the appropriate currency symbol
 * @param priceAmount The price amount as a number
 * @param currencyCode The ISO 4217 currency code (e.g., 'USD', 'EUR')
 * @returns Formatted price string
 */
export function formatPrice(priceAmount: number, currencyCode: string): string {
  return new Intl.NumberFormat(navigator.language, {
    style: 'currency',
    currency: currencyCode,
  }).format(priceAmount);
}