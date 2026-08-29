package com.example.meritrankerstudent.ui.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.billing.BillingManager
import com.example.meritrankerstudent.data.billing.BillingState
import com.example.meritrankerstudent.data.billing.CreditPackConfig
import com.example.meritrankerstudent.data.billing.CreditPackItemState
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultGooglePlayBillingRepository
import com.example.meritrankerstudent.data.repository.GooglePlayBillingRepository
import com.example.meritrankerstudent.data.repository.UserCreditsInfo
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the MeritRanker Credit Pack Store.
 * Bridges UI BottomSheet to the application-scoped BillingManager and authoritative UserCredits.
 */
class CreditStoreViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = DefaultAuthRepository()
    private val billingRepository: GooglePlayBillingRepository = DefaultGooglePlayBillingRepository(authRepository)
    private val billingManager: BillingManager = BillingManager.getInstance(
        context = application,
        billingRepository = billingRepository,
        authRepository = authRepository
    )

    val billingState: StateFlow<BillingState> = billingManager.billingState
    val packItemStates: StateFlow<List<CreditPackItemState>> = billingManager.packItemStates
    val userCredits: StateFlow<UserCreditsInfo?> = billingRepository.userCredits

    init {
        AppObservability.analytics.logEvent(TelemetryEvent.CreditStoreOpened)
        refreshCredits()
    }

    fun refreshCredits() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (!userId.isNullOrBlank()) {
                    billingRepository.fetchUserCredits(userId)
                }
            } catch (_: Exception) {
                // Ignore background sync errors
            }
        }
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
