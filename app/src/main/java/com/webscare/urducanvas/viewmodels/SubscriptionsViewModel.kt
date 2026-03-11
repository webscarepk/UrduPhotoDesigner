package com.webscare.urducanvas.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.webscare.urducanvas.di.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val billingManager: BillingManager
) : ViewModel() {

    val billingState: StateFlow<BillingManager.BillingState> = billingManager.billingState

    fun loadProducts() = billingManager.queryProducts()

    fun subscribe(activity: Activity, planId: Int) =
        billingManager.launchPurchase(activity, planId)

    fun restore() = billingManager.restorePurchases()

    fun resetState() = billingManager.resetState()
}