package com.example.payment

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.auth.MembershipPlan
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages initialization of the official Razorpay Android Checkout SDK and launches
 * the payment window using an authoritative order ID created by the PaperPilot backend.
 *
 * Security notes:
 * - Only the public Razorpay Key ID (`rzp_test_...` / `rzp_live_...`) is used here.
 * - `RAZORPAY_KEY_SECRET` NEVER exists in client-side Android code.
 * - Client-side `onPaymentSuccess` callbacks NEVER activate Premium directly; they emit
 *   [RazorpayCheckoutEvent.PaymentCapturedPendingVerification] so the server can verify
 *   the HMAC-SHA256 signature via `POST /api/payment/verify`.
 */
class PaymentManager(private val appContext: Context) {

    /**
     * Stream of Razorpay Checkout lifecycle events emitted after [MainActivity] forwards
     * [com.razorpay.PaymentResultWithDataListener] callbacks.
     */
    val events: SharedFlow<RazorpayCheckoutEvent> = Companion.events

    /**
     * Initializes / preloads the Razorpay Checkout SDK so the checkout sheet opens swiftly.
     * Safe to invoke multiple times across activity lifecycles.
     */
    fun initialize(): Boolean {
        if (isInitialized.compareAndSet(false, true)) {
            return runCatching {
                Checkout.preload(appContext.applicationContext)
                true
            }.onFailure { err ->
                isInitialized.set(false)
                Log.w(TAG, "Razorpay Checkout preload failed: ${err.message}")
            }.getOrDefault(false)
        }
        return true
    }

    /**
     * Triggers the Razorpay Checkout payment window using a [ServerOrderResponse] returned
     * by `POST /api/payment/create-order`.
     */
    fun startPayment(
        activity: Activity,
        order: ServerOrderResponse,
        plan: MembershipPlan,
        method: PaymentMethodOption,
        customerEmail: String = "",
        customerName: String = "",
        customerContact: String = ""
    ): Result<Unit> = startPayment(
        activity = activity,
        orderId = order.razorpayOrderId,
        subscriptionId = order.razorpaySubscriptionId,
        amountPaise = order.amountPaise,
        razorpayKeyId = order.razorpayKeyId,
        currency = order.currency.ifBlank { "INR" },
        description = if (plan.isTrial || order.autoPayEnabled) {
            val nextAmt = if (order.nextChargeAmountInr > 0L) "₹${order.nextChargeAmountInr}" else "₹99"
            "1-Day Trial (₹1) + UPI AutoPay ($nextAmt after trial)"
        } else {
            "${plan.title} (${plan.priceDisplay}/${plan.billingPeriod})"
        },
        customerEmail = customerEmail,
        customerName = customerName,
        customerContact = customerContact,
        internalOrderId = order.orderId,
        planTierId = plan.tierId,
        paymentMethodLabel = if (order.autoPayEnabled) "UPI AutoPay (${method.title})" else method.title,
        enableRecurringAutoPay = order.autoPayEnabled || order.razorpaySubscriptionId.isNotBlank()
    )

    /**
     * Triggers the Razorpay Checkout payment window using an authoritative backend [orderId]
     * or AutoPay [subscriptionId] and [amountPaise] (e.g., `100` for ₹1 trial, `9900` for ₹99/month, `99900` for ₹999/year).
     */
    fun startPayment(
        activity: Activity,
        orderId: String,
        amountPaise: Long,
        razorpayKeyId: String = BuildConfig.RAZORPAY_PUBLIC_KEY_ID,
        currency: String = "INR",
        description: String = "PaperPilot Premium Membership",
        customerEmail: String = "",
        customerName: String = "",
        customerContact: String = "",
        internalOrderId: String = orderId,
        planTierId: String = "",
        paymentMethodLabel: String = "Razorpay",
        subscriptionId: String = "",
        enableRecurringAutoPay: Boolean = false
    ): Result<Unit> = runCatching {
        initialize()

        val resolvedKeyId = validatePublicKey(razorpayKeyId.ifBlank { BuildConfig.RAZORPAY_PUBLIC_KEY_ID })
        val normalizedOrderId = orderId.trim()
        val normalizedSubId = subscriptionId.trim()
        require(normalizedOrderId.isNotBlank() || normalizedSubId.isNotBlank()) {
            "A valid backend Razorpay order ID or AutoPay subscription ID is required before opening Checkout."
        }
        require(amountPaise > 0L) {
            "Payment amount in paise must be greater than zero."
        }

        val options = buildCheckoutOptions(
            orderId = normalizedOrderId,
            subscriptionId = normalizedSubId,
            amountPaise = amountPaise,
            currency = currency,
            description = description,
            customerEmail = customerEmail,
            customerName = customerName,
            customerContact = customerContact,
            internalOrderId = internalOrderId.ifBlank { normalizedSubId.ifBlank { normalizedOrderId } },
            planTierId = planTierId,
            enableRecurringAutoPay = enableRecurringAutoPay || normalizedSubId.isNotBlank()
        )

        activeInternalOrderId = internalOrderId.ifBlank { normalizedSubId.ifBlank { normalizedOrderId } }
        activeRazorpayOrderId = normalizedOrderId
        activeRazorpaySubscriptionId = normalizedSubId
        activeMethodLabel = paymentMethodLabel.ifBlank { "Razorpay" }

        val checkout = Checkout()
        checkout.setKeyID(resolvedKeyId)
        checkout.open(activity, options)
    }

    /**
     * Builds the Razorpay Checkout SDK options JSON payload with PaperPilot branding.
     * Supports both standard one-time/order checkout (`order_id`) and UPI AutoPay (`subscription_id` + `recurring`).
     */
    internal fun buildCheckoutOptions(
        orderId: String,
        amountPaise: Long,
        currency: String = "INR",
        description: String = "PaperPilot Premium Membership",
        customerEmail: String = "",
        customerName: String = "",
        customerContact: String = "",
        internalOrderId: String = orderId,
        planTierId: String = "",
        subscriptionId: String = "",
        enableRecurringAutoPay: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("name", MERCHANT_NAME)
        put("description", description)
        if (subscriptionId.isNotBlank()) {
            put("subscription_id", subscriptionId)
            put("recurring", "1")
        } else {
            put("order_id", orderId)
            if (enableRecurringAutoPay) {
                put("recurring", "1")
            }
        }
        put("currency", currency.ifBlank { "INR" })
        put("amount", amountPaise)
        put("retry", JSONObject().apply {
            put("enabled", true)
            put("max_count", 2)
        })
        put("theme", JSONObject().apply {
            put("color", BRAND_THEME_HEX)
        })
        put("prefill", JSONObject().apply {
            if (customerEmail.isNotBlank()) put("email", customerEmail.trim())
            if (customerName.isNotBlank()) put("name", customerName.trim())
            if (customerContact.isNotBlank()) put("contact", customerContact.trim())
        })
        put("notes", JSONObject().apply {
            put("internal_order_id", internalOrderId)
            if (planTierId.isNotBlank()) put("plan_tier", planTierId)
            if (enableRecurringAutoPay || subscriptionId.isNotBlank()) put("autopay", "true")
        })
    }

    companion object {
        private const val TAG = "PaymentManager"
        private const val MERCHANT_NAME = "PaperPilot — All-in-One PDF Editor"
        private const val BRAND_THEME_HEX = "#1565C0"

        private val isInitialized = AtomicBoolean(false)

        private val _events = MutableSharedFlow<RazorpayCheckoutEvent>(extraBufferCapacity = 8)
        val events: SharedFlow<RazorpayCheckoutEvent> = _events.asSharedFlow()

        @Volatile
        private var activeInternalOrderId: String = ""

        @Volatile
        private var activeRazorpayOrderId: String = ""

        @Volatile
        private var activeRazorpaySubscriptionId: String = ""

        @Volatile
        private var activeMethodLabel: String = "Razorpay"

        internal fun validatePublicKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.isBlank() ||
                trimmed.contains("replace_me", ignoreCase = true) ||
                trimmed.contains("replace_with_", ignoreCase = true)
            ) {
                throw IllegalStateException(
                    "Razorpay Key ID is not configured on the backend. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in backend/.env and redeploy."
                )
            }
            return trimmed
        }

        /**
         * Forwards [com.razorpay.PaymentResultWithDataListener.onPaymentSuccess] from [MainActivity].
         * Requires a non-empty signature before dispatching to server verification.
         */
        fun handlePaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
            val paymentId = paymentData?.paymentId?.takeIf { it.isNotBlank() } ?: razorpayPaymentId.orEmpty()
            val rzpOrderId = paymentData?.orderId?.takeIf { it.isNotBlank() } ?: activeRazorpayOrderId
            val signature = paymentData?.signature.orEmpty()

            if (paymentId.isBlank() || signature.isBlank()) {
                _events.tryEmit(
                    RazorpayCheckoutEvent.PaymentFailed(
                        internalOrderId = activeInternalOrderId,
                        code = -1,
                        message = "Missing Razorpay payment signature. Membership will not be activated without server verification."
                    )
                )
                return
            }

            _events.tryEmit(
                RazorpayCheckoutEvent.PaymentCapturedPendingVerification(
                    internalOrderId = activeInternalOrderId,
                    razorpayOrderId = rzpOrderId,
                    razorpaySubscriptionId = activeRazorpaySubscriptionId,
                    razorpayPaymentId = paymentId,
                    razorpaySignature = signature,
                    paymentMethodLabel = activeMethodLabel
                )
            )
        }

        /**
         * Forwards [com.razorpay.PaymentResultWithDataListener.onPaymentError] from [MainActivity].
         */
        fun handlePaymentError(code: Int, response: String?, paymentData: PaymentData?) {
            val cleanReason = parseErrorReason(response)
            if (code == Checkout.PAYMENT_CANCELED) {
                _events.tryEmit(
                    RazorpayCheckoutEvent.PaymentCancelled(
                        internalOrderId = activeInternalOrderId,
                        message = cleanReason.ifBlank { "Payment was cancelled before completion." }
                    )
                )
            } else {
                _events.tryEmit(
                    RazorpayCheckoutEvent.PaymentFailed(
                        internalOrderId = activeInternalOrderId,
                        code = code,
                        message = cleanReason.ifBlank { "Razorpay payment failed (code $code)." }
                    )
                )
            }
        }

        private fun parseErrorReason(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return runCatching {
                val json = JSONObject(raw)
                val errObj = json.optJSONObject("error")
                errObj?.optString("description")?.takeIf { it.isNotBlank() }
                    ?: errObj?.optString("reason")?.takeIf { it.isNotBlank() }
                    ?: raw.take(160)
            }.getOrElse { raw.take(160) }
        }
    }
}
