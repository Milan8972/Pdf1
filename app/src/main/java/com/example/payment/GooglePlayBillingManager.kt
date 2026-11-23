package com.example.payment

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.auth.MembershipPlan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class PlayBillingEvent {
    data class PurchaseReadyForServerVerification(
        val productId: String,
        val purchaseToken: String,
        val orderId: String,
        val isAcknowledged: Boolean
    ) : PlayBillingEvent()

    data class PurchaseCancelled(val message: String) : PlayBillingEvent()
    data class PurchaseFailed(val code: Int, val message: String) : PlayBillingEvent()
}

/**
 * Official Google Play Billing Library (v7) client for PaperPilot subscriptions:
 * - paperpilot_premium_monthly (₹99/month)
 * - paperpilot_premium_yearly (₹999/year)
 *
 * Strictly follows Google Play Billing policy:
 * 1. Launches native Play Billing sheet.
 * 2. Sends purchaseToken to secure backend for server-side verification BEFORE activating Premium.
 * 3. Acknowledges subscription ONLY after backend verification succeeds.
 * 4. Supports full Purchase Restoration via queryPurchasesAsync.
 */
class GooglePlayBillingManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<MembershipPlan, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<MembershipPlan, ProductDetails>> = _productDetails.asStateFlow()

    private val _events = MutableSharedFlow<PlayBillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayBillingEvent> = _events.asSharedFlow()

    fun connect(onReady: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _isConnected.value = true
            querySubscriptionProducts()
            onReady?.invoke()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    querySubscriptionProducts()
                    onReady?.invoke()
                } else {
                    _isConnected.value = false
                    Log.w(TAG, "Play Billing setup unavailable: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
            }
        })
    }

    fun querySubscriptionProducts() {
        if (!billingClient.isReady) return
        val productList = MembershipPlan.paidPlans.map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.playProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val mapped = mutableMapOf<MembershipPlan, ProductDetails>()
                detailsList.forEach { details ->
                    MembershipPlan.fromPlayProductId(details.productId)?.let { plan ->
                        mapped[plan] = details
                    }
                }
                _productDetails.value = mapped
            } else {
                Log.w(TAG, "Failed to query Play subscription products: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity, plan: MembershipPlan): Result<Unit> {
        if (!billingClient.isReady) {
            connect()
            return Result.failure(
                IllegalStateException(
                    "Google Play Billing is not ready on this device. Sign in to Google Play Store and ensure products ${plan.playProductId} are configured in Play Console."
                )
            )
        }

        val details = _productDetails.value[plan]
            ?: return Result.failure(
                IllegalStateException(
                    "Subscription SKU '${plan.playProductId}' (${plan.priceDisplay}/${plan.billingPeriod}) is not yet published in Google Play Console for this app package."
                )
            )

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(
                IllegalStateException("No active base plan offer found for ${plan.playProductId}.")
            )

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.debugMessage.ifBlank { "Google Play Billing launch failed (${result.responseCode})." }))
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val productId = purchase.products.firstOrNull().orEmpty()
                        _events.tryEmit(
                            PlayBillingEvent.PurchaseReadyForServerVerification(
                                productId = productId,
                                purchaseToken = purchase.purchaseToken,
                                orderId = purchase.orderId.orEmpty(),
                                isAcknowledged = purchase.isAcknowledged
                            )
                        )
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        _events.tryEmit(
                            PlayBillingEvent.PurchaseFailed(
                                code = billingResult.responseCode,
                                message = "Payment is pending confirmation from Google Play. Membership will activate automatically once verified."
                            )
                        )
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _events.tryEmit(PlayBillingEvent.PurchaseCancelled("Purchase was cancelled before completion."))
            }
            else -> {
                _events.tryEmit(
                    PlayBillingEvent.PurchaseFailed(
                        code = billingResult.responseCode,
                        message = billingResult.debugMessage.ifBlank { "Google Play Billing error (${billingResult.responseCode})." }
                    )
                )
            }
        }
    }

    suspend fun acknowledgePurchase(purchaseToken: String): Boolean =
        suspendCancellableCoroutine { cont ->
            if (!billingClient.isReady || purchaseToken.isBlank()) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (cont.isActive) {
                    cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }
        }

    suspend fun queryActiveSubscriptions(): List<Purchase> =
        suspendCancellableCoroutine { cont ->
            if (!billingClient.isReady) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (cont.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED })
                    } else {
                        cont.resume(emptyList())
                    }
                }
            }
        }

    fun endConnection() {
        runCatching { billingClient.endConnection() }
        _isConnected.value = false
    }
}
