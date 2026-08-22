package com.example.meritrankerstudent.ui.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.billing.BillingManager
import com.example.meritrankerstudent.data.billing.BillingUiState
import com.example.meritrankerstudent.data.billing.PlayProductDetails
import com.example.meritrankerstudent.data.billing.PurchaseTransactionRecord
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    application: Application,
    private val billingManager: BillingManager = BillingManager.getInstance(application)
) : AndroidViewModel(application) {

    val uiState: StateFlow<BillingUiState> = billingManager.uiState
    val availableProducts: StateFlow<List<PlayProductDetails>> = billingManager.availableProducts
    val activeSubscription: StateFlow<PurchaseTransactionRecord?> = billingManager.activeSubscription
    val recentTransactions: StateFlow<List<PurchaseTransactionRecord>> = billingManager.recentTransactions

    fun launchPurchase(activity: Activity, product: PlayProductDetails) {
        billingManager.launchPurchase(activity, product)
    }

    fun restorePurchases() {
        billingManager.reconcilePurchases()
    }

    fun dismissState() {
        billingManager.dismissState()
    }

    fun retryConnection() {
        billingManager.startBillingConnection()
        billingManager.queryAvailableProducts()
    }
}
