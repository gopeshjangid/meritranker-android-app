package com.example.meritrankerstudent.ui.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.meritrankerstudent.data.billing.BillingManager
import com.example.meritrankerstudent.data.billing.BillingState
import com.example.meritrankerstudent.data.billing.CreditPackConfig
import com.example.meritrankerstudent.data.billing.CreditPackItemState
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the MeritRanker Credit Pack Store.
 * Bridges UI BottomSheet to the application-scoped BillingManager.
 */
class CreditStoreViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val billingManager: BillingManager = BillingManager.getInstance(application)

    val billingState: StateFlow<BillingState> = billingManager.billingState
    val packItemStates: StateFlow<List<CreditPackItemState>> = billingManager.packItemStates

    init {
        AppObservability.analytics.logEvent(TelemetryEvent.CreditStoreOpened)
    }

    fun launchBuy(activity: Activity, packConfig: CreditPackConfig): Boolean {
        AppObservability.analytics.logEvent(
            TelemetryEvent.CreditPackSelected(
                planId = packConfig.localPlanId,
                credits = packConfig.credits
            )
        )
        return billingManager.launchPurchase(activity, packConfig)
    }

    fun refreshCatalog() {
        billingManager.queryProductCatalog()
    }

    fun reconcilePurchases() {
        billingManager.reconcilePurchases()
    }

    fun dismissState() {
        billingManager.dismissState()
    }
}
