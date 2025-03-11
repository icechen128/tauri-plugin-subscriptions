import Foundation
import StoreKit

@objc(Subscriptions)
public class Subscriptions: NSObject {
    
    // MARK: - Properties
    
    private let productIdentifiers: Set<String> = []
    private var productsRequest: SKProductsRequest?
    private var completionHandler: (([SKProduct], Error?) -> Void)?
    private var purchaseCompletionHandler: ((SKPaymentTransaction?, Error?) -> Void)?
    private var restoreCompletionHandler: (([SKPaymentTransaction]?, Error?) -> Void)?
    
    // Track purchased products and their receipts
    private var purchasedProducts: [String: SKPaymentTransaction] = [:]
    private var activeSubscriptions: [String: Date] = [:]
    
    // MARK: - Init
    
    @objc public static func register() {
        // Register for transaction notifications
        SKPaymentQueue.default().add(Subscriptions.shared)
    }
    
    @objc public static let shared = Subscriptions()
    
    private override init() {
        super.init()
    }
    
    // MARK: - Public Methods
    
    @objc public func getProducts(_ productIdentifiers: [String], completion: @escaping ([SKProduct], Error?) -> Void) {
        self.completionHandler = completion
        
        let request = SKProductsRequest(productIdentifiers: Set(productIdentifiers))
        request.delegate = self
        request.start()
        
        self.productsRequest = request
    }
    
    @objc public func purchaseProduct(_ product: SKProduct, completion: @escaping (SKPaymentTransaction?, Error?) -> Void) {
        guard SKPaymentQueue.canMakePayments() else {
            completion(nil, NSError(domain: "com.tauri.subscriptions", code: -1, userInfo: [NSLocalizedDescriptionKey: "In-app purchases are not allowed"]))
            return
        }
        
        self.purchaseCompletionHandler = completion
        
        let payment = SKPayment(product: product)
        SKPaymentQueue.default().add(payment)
    }
    
    @objc public func restorePurchases(completion: @escaping ([SKPaymentTransaction]?, Error?) -> Void) {
        self.restoreCompletionHandler = completion
        SKPaymentQueue.default().restoreCompletedTransactions()
    }
    
    @objc public func getSubscriptionStatus(_ productId: String) -> [String: Any] {
        var status: [String: Any] = [
            "productId": productId,
            "isActive": false,
            "expiryDate": nil,
            "autoRenewStatus": false,
            "isInTrialPeriod": false,
            "isInGracePeriod": false
        ]
        
        if let expiryDate = activeSubscriptions[productId] {
            let isActive = expiryDate > Date()
            status["isActive"] = isActive
            status["expiryDate"] = expiryDate.timeIntervalSince1970
            
            // These values would need to be extracted from receipt validation in a real implementation
            status["autoRenewStatus"] = true
            status["isInTrialPeriod"] = false
            status["isInGracePeriod"] = false
        }
        
        return status
    }
    
    // MARK: - Helper Methods
    
    private func validateReceipt(forTransaction transaction: SKPaymentTransaction) {
        // In a real app, you would implement App Store receipt validation here
        // For now, we'll simulate subscription expiry dates
        
        if transaction.payment.productIdentifier.contains("subscription") {
            // Set expiry to 1 month from now for demo purposes
            let expiryDate = Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date()
            activeSubscriptions[transaction.payment.productIdentifier] = expiryDate
        } else {
            // For non-subscription purchases, just mark as purchased
            purchasedProducts[transaction.payment.productIdentifier] = transaction
        }
    }
}

// MARK: - SKProductsRequestDelegate

extension Subscriptions: SKProductsRequestDelegate {
    public func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        DispatchQueue.main.async {
            self.completionHandler?(response.products, nil)
            self.completionHandler = nil
            self.productsRequest = nil
        }
    }
    
    public func request(_ request: SKRequest, didFailWithError error: Error) {
        DispatchQueue.main.async {
            self.completionHandler?([], error)
            self.completionHandler = nil
            self.productsRequest = nil
        }
    }
}

// MARK: - SKPaymentTransactionObserver

extension Subscriptions: SKPaymentTransactionObserver {
    public func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            switch transaction.transactionState {
            case .purchased:
                validateReceipt(forTransaction: transaction)
                SKPaymentQueue.default().finishTransaction(transaction)
                purchaseCompletionHandler?(transaction, nil)
                purchaseCompletionHandler = nil
                
            case .failed:
                SKPaymentQueue.default().finishTransaction(transaction)
                purchaseCompletionHandler?(nil, transaction.error)
                purchaseCompletionHandler = nil
                
            case .restored:
                validateReceipt(forTransaction: transaction)
                SKPaymentQueue.default().finishTransaction(transaction)
                
            case .purchasing, .deferred:
                break
            @unknown default:
                break
            }
        }
    }
    
    public func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        let restoredTransactions = queue.transactions.filter { $0.transactionState == .restored }
        
        for transaction in restoredTransactions {
            validateReceipt(forTransaction: transaction)
        }
        
        DispatchQueue.main.async {
            self.restoreCompletionHandler?(restoredTransactions, nil)
            self.restoreCompletionHandler = nil
        }
    }
    
    public func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        DispatchQueue.main.async {
            self.restoreCompletionHandler?(nil, error)
            self.restoreCompletionHandler = nil
        }
    }
}