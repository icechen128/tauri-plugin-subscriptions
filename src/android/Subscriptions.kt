package com.tauri.subscriptions

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import org.json.JSONObject

class Subscriptions(private val context: Context) : PurchasesUpdatedListener, BillingClientStateListener {

    private val _productsFlow = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productsFlow = _productsFlow.asStateFlow()

    private val _purchasesFlow = MutableStateFlow<List<Purchase>>(emptyList())
    val purchasesFlow = _purchasesFlow.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var isServiceConnected = false
    private var productsMap = mutableMapOf<String, ProductDetails>()
    private var purchasesMap = mutableMapOf<String, Purchase>()

    // Callbacks
    private var productDetailsCallback: ((List<ProductDetails>?, BillingResult?) -> Unit)? = null
    private var purchaseCallback: ((Purchase?, BillingResult?) -> Unit)? = null
    private var restoreCallback: ((List<Purchase>?, BillingResult?) -> Unit)? = null

    init {
        billingClient.startConnection(this)
    }

    // BillingClientStateListener implementation
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            isServiceConnected = true
            // Query existing purchases
            queryPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        isServiceConnected = false
        // Try to reconnect
        billingClient.startConnection(this)
    }

    // PurchasesUpdatedListener implementation
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
            purchaseCallback?.invoke(purchases.firstOrNull(), billingResult)
        } else {
            purchaseCallback?.invoke(null, billingResult)
        }
    }

    // Public methods
    fun getProducts(productIds: List<String>, callback: (List<ProductDetails>?, BillingResult?) -> Unit) {
        productDetailsCallback = callback

        if (!isServiceConnected) {
            callback(null, BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing service disconnected")
                .build())
            return
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productsFlow.value = productDetailsList
                for (details in productDetailsList) {
                    productsMap[details.productId] = details
                }
            }
            callback(productDetailsList, billingResult)
        }
    }

    fun purchaseProduct(activity: Activity, productDetails: ProductDetails, offerToken: String? = null) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (offerToken != null) {
                        setOfferToken(offerToken)
                    }
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    fun purchaseProductById(activity: Activity, productId: String, callback: (Purchase?, BillingResult?) -> Unit) {
        purchaseCallback = callback
        
        val productDetails = productsMap[productId]
        if (productDetails == null) {
            callback(null, BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("Product not found")
                .build())
            return
        }

        // For subscriptions, you may want to select a specific offer
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        purchaseProduct(activity, productDetails, offerToken)
    }

    fun restorePurchases(callback: (List<Purchase>?, BillingResult?) -> Unit) {
        restoreCallback = callback

        if (!isServiceConnected) {
            callback(null, BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing service disconnected")
                .build())
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesList) {
                    handlePurchase(purchase)
                }
            }
            callback(purchasesList, billingResult)
        }
    }

    fun getSubscriptionStatus(productId: String): Map<String, Any?> {
        val purchase = purchasesMap.values.firstOrNull { 
            it.products.contains(productId) && 
            it.purchaseState == Purchase.PurchaseState.PURCHASED 
        }

        return mapOf(
            "productId" to productId,
            "isActive" to (purchase != null && purchase.isAcknowledged),
            "expiryDate" to null, // This requires server-side validation with Google
            "autoRenewStatus" to true, // This requires server-side validation with Google
            "isInTrialPeriod" to false, // This requires server-side validation with Google
            "isInGracePeriod" to false // This requires server-side validation with Google
        )
    }

    // Private helper methods
    private fun queryPurchases() {
        if (!isServiceConnected) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchasesFlow.value = purchasesList
                for (purchase in purchasesList) {
                    purchasesMap[purchase.orderId ?: ""] = purchase
                    handlePurchase(purchase)
                }
            }
        }

        // Also query one-time purchases
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(inappParams) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val currentList = _purchasesFlow.value.toMutableList()
                currentList.addAll(purchasesList)
                _purchasesFlow.value = currentList

                for (purchase in purchasesList) {
                    purchasesMap[purchase.orderId ?: ""] = purchase
                    handlePurchase(purchase)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase if it hasn't been acknowledged yet
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {// Purchase successfully acknowledged
                        val updatedPurchases = _purchasesFlow.value.toMutableList()
                        val index = updatedPurchases.indexOfFirst { it.orderId == purchase.orderId }
                        if (index != -1) {
                            // This would be a new purchase object with isAcknowledged set to true
                            // In real implementation, we would query again to get the updated purchase
                            updatedPurchases[index] = purchase
                            _purchasesFlow.value = updatedPurchases
                        }
                    }
                }
            }

            // Verify the purchase on your server and provide content to the user
            // In a real app, you should verify the purchase on your server
        }
    }

    // Helper method to check if a subscription is active
    fun isSubscriptionActive(productId: String): Boolean {
        return purchasesMap.values.any { 
            it.products.contains(productId) && 
            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
            it.isAcknowledged
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: Subscriptions? = null

        fun getInstance(context: Context): Subscriptions {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Subscriptions(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}