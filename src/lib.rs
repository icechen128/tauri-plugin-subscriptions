use serde::{Deserialize, Serialize};
use tauri::{
    plugin::{Builder, TauriPlugin},
    Runtime, Window, AppHandle, Manager,
};
use std::collections::HashMap;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("Failed to retrieve products: {0}")]
    ProductRetrievalError(String),
    
    #[error("Purchase failed: {0}")]
    PurchaseError(String),
    
    #[error("Subscription management error: {0}")]
    SubscriptionError(String),
    
    #[error("Validation error: {0}")]
    ValidationError(String),
    
    #[error("Platform-specific error: {0}")]
    PlatformError(String),
}

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub enum ProductType {
    Consumable,
    NonConsumable,
    Subscription,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub enum SubscriptionPeriod {
    Day,
    Week,
    Month,
    Year,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Product {
    id: String,
    title: String,
    description: String,
    price: String,
    price_amount: f64,
    currency_code: String,
    product_type: ProductType,
    subscription_period: Option<SubscriptionPeriod>,
    subscription_period_unit: Option<u32>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct PurchaseResult {
    product_id: String,
    transaction_id: String,
    purchase_time: u64,
    is_acknowledged: bool,
    subscription_expiry_time: Option<u64>,
    receipt_data: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SubscriptionStatus {
    product_id: String,
    is_active: bool,
    expiry_date: Option<u64>,
    auto_renew_status: bool,
    is_in_trial_period: bool,
    is_in_grace_period: bool,
}

#[cfg(target_os = "ios")]
mod ios;
#[cfg(target_os = "ios")]
use ios::*;

#[cfg(target_os = "android")]
mod android;
#[cfg(target_os = "android")]
use android::*;

#[tauri::command]
async fn get_products(
    app: AppHandle,
    product_ids: Vec<String>,
) -> Result<Vec<Product>> {
    #[cfg(target_os = "ios")]
    {
        return ios::get_products_ios(app, product_ids).await;
    }
    
    #[cfg(target_os = "android")]
    {
        return android::get_products_android(app, product_ids).await;
    }
    
    #[cfg(not(any(target_os = "ios", target_os = "android")))]
    {
        Err(Error::PlatformError("Subscriptions are only supported on iOS and Android".to_string()))
    }
}

#[tauri::command]
async fn purchase_product(
    app: AppHandle,
    product_id: String,
) -> Result<PurchaseResult> {
    #[cfg(target_os = "ios")]
    {
        return ios::purchase_product_ios(app, product_id).await;
    }
    
    #[cfg(target_os = "android")]
    {
        return android::purchase_product_android(app, product_id).await;
    }
    
    #[cfg(not(any(target_os = "ios", target_os = "android")))]
    {
        Err(Error::PlatformError("Purchases are only supported on iOS and Android".to_string()))
    }
}

#[tauri::command]
async fn restore_purchases(
    app: AppHandle,
) -> Result<Vec<PurchaseResult>> {
    #[cfg(target_os = "ios")]
    {
        return ios::restore_purchases_ios(app).await;
    }
    
    #[cfg(target_os = "android")]
    {
        return android::restore_purchases_android(app).await;
    }
    
    #[cfg(not(any(target_os = "ios", target_os = "android")))]
    {
        Err(Error::PlatformError("Restoring purchases is only supported on iOS and Android".to_string()))
    }
}

#[tauri::command]
async fn get_subscription_status(
    app: AppHandle,
    product_id: String,
) -> Result<SubscriptionStatus> {
    #[cfg(target_os = "ios")]
    {
        return ios::get_subscription_status_ios(app, product_id).await;
    }
    
    #[cfg(target_os = "android")]
    {
        return android::get_subscription_status_android(app, product_id).await;
    }
    
    #[cfg(not(any(target_os = "ios", target_os = "android")))]
    {
        Err(Error::PlatformError("Subscription status checking is only supported on iOS and Android".to_string()))
    }
}

/// Initialize the plugin
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("subscriptions")
        .invoke_handler(tauri::generate_handler![
            get_products,
            purchase_product,
            restore_purchases,
            get_subscription_status,
        ])
        .setup(|app_handle| {
            #[cfg(target_os = "ios")]
            {
                ios::init_ios(app_handle)?;
            }
            
            #[cfg(target_os = "android")]
            {
                android::init_android(app_handle)?;
            }
            
            Ok(())
        })
        .build()
}