# tauri-plugin-subscriptions

# Tauri Plugin: Subscriptions

A Tauri plugin for handling in-app purchases and subscriptions on iOS and Android platforms, leveraging Apple's StoreKit API and Google's Play Billing Library.

## Features

- Support for both one-time purchases and subscriptions
- Cross-platform API for iOS and Android
- TypeScript bindings for seamless integration with SvelteKit
- Product listing and retrieval
- Purchase flow management
- Subscription status tracking
- Purchase restoration

## Installation

```bash
npm install tauri-plugin-subscriptions
# or
yarn add tauri-plugin-subscriptions
```

## Mobile Setup

### iOS

1. Add the plugin in your `Cargo.toml`:

```toml
[dependencies]
tauri-plugin-subscriptions = { git = "https://github.com/yourusername/tauri-plugin-subscriptions" }
```

2. Add StoreKit capability to your Xcode project:
   - Open your iOS project in Xcode
   - Select your app target
   - Go to "Signing & Capabilities"
   - Click "+" and add "In-App Purchase" capability

3. Configure your in-app purchases in App Store Connect.

### Android

1. Add the plugin in your `Cargo.toml`:

```toml
[dependencies]
tauri-plugin-subscriptions = { git = "https://github.com/yourusername/tauri-plugin-subscriptions" }
```

2. Add Google Play Billing Library to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.android.billingclient:billing:6.0.1'
}
```

3. Configure your in-app products in Google Play Console.

## Usage in Rust

```rust
fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_subscriptions::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

## Usage in SvelteKit

```typescript
import { getProducts, purchaseProduct, isSubscriptionActive } from 'tauri-plugin-subscriptions';

// Get available products
const products = await getProducts(['com.example.subscription.monthly', 'com.example.subscription.yearly']);

// Purchase a product
try {
  const result = await purchaseProduct('com.example.subscription.monthly');
  console.log('Purchase successful:', result);
} catch (error) {
  console.error('Purchase failed:', error);
}

// Check subscription status
const isActive = await isSubscriptionActive('com.example.subscription.monthly');
if (isActive) {
  // User has an active subscription
}
```

## API Reference

### TypeScript API

#### Product Interface

```typescript
interface Product {
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
```

#### Methods

- `getProducts(productIds: string[]): Promise<Product[]>`
- `purchaseProduct(productId: string): Promise<PurchaseResult>`
- `restorePurchases(): Promise<PurchaseResult[]>`
- `getSubscriptionStatus(productId: string): Promise<SubscriptionStatus>`
- `isSubscriptionActive(productId: string): Promise<boolean>`
- `getSubscriptionExpiryDate(productId: string): Promise<number | null>`
- `formatPrice(priceAmount: number, currencyCode: string): string`

## License

MIT


# Integration Guide: Adding Subscriptions to Your app

This guide walks through the process of integrating the `tauri-plugin-subscriptions` plugin into your app.

## 1. Installation and Setup

### Install the Plugin

First, add the plugin to your Cargo.toml file:

```toml
# src-tauri/Cargo.toml
[dependencies]
tauri-plugin-subscriptions = { git = "https://github.com/yourusername/tauri-plugin-subscriptions" }
```

### Initialize the Plugin in Rust

Modify your `src-tauri/src/lib.rs` file to include the subscriptions plugin:

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    #[cfg(mobile)]
    tauri::Builder::default()
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_barcode_scanner::init())
        .plugin(tauri_plugin_subscriptions::init()) // Add this line
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
    #[cfg(not(mobile))]
    tauri::Builder::default()
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_subscriptions::init()) // Add this line
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### Install TypeScript Types

For TypeScript support, install the npm package:

```bash
npm install tauri-plugin-subscriptions
```

## 2. Platform-Specific Configuration

### iOS Configuration

1. Open your Xcode project located in `src-tauri/gen/apple/IntakeAI`
2. Select your app target → "Signing & Capabilities"
3. Click "+" → Add "In-App Purchase" capability
4. Update your Info.plist file to include:
   ```xml
   <key>SKAdNetworkItems</key>
   <array>
     <dict>
       <key>SKAdNetworkIdentifier</key>
       <string>2U9PT9HC89.skadnetwork</string>
     </dict>
   </array>
   ```

5. Configure your in-app purchases in App Store Connect:
   - Go to your app in App Store Connect
   - Navigate to "Features" → "In-App Purchases"
   - Create your subscription products
     - Monthly subscription
     - Yearly subscription
   - Set up subscription groups, pricing, and descriptions

### Android Configuration

1. Open your Android project in Android Studio located in `src-tauri/gen/android`
2. Add Google Play Billing Library to your app-level `build.gradle`:
   ```gradle
   dependencies {
     implementation 'com.android.billingclient:billing:6.0.1'
   }
   ```

3. Update your `AndroidManifest.xml` to include:
   ```xml
   <uses-permission android:name="com.android.vending.BILLING" />
   ```

4. Configure your in-app products in Google Play Console:
   - Create subscription products with the same IDs you'll use in your app
   - Set up pricing and subscription details

## 3. Implementation in Your SvelteKit App

### Create a Subscription Page

Create a new subscription page component (e.g., `src/routes/subscription/+page.svelte`) using the example component provided.

### Add Subscription Check at App Startup

Modify your app's main layout or startup flow to check subscription status:

```typescript
// In src/routes/+layout.ts or similar
import { onMount } from 'svelte';
import { isSubscriptionActive } from 'tauri-plugin-subscriptions';
import { goto } from '$app/navigation';
import { subscriptionStatus } from '$lib/stores'; // Create this store

onMount(async () => {
  try {
    // Check if user has an active subscription
    const hasSubscription = await isSubscriptionActive('com.intakeai.subscription.monthly') || 
                            await isSubscriptionActive('com.intakeai.subscription.yearly');
    
    // Update your app's subscription state
    subscriptionStatus.set(hasSubscription);
    
    // If no active subscription, redirect to subscription page
    if (!hasSubscription) {
      goto('/subscription');
    }