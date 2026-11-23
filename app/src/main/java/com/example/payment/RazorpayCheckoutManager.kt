package com.example.payment

import android.app.Activity
import android.content.Context
import com.example.auth.MembershipPlan
import com.razorpay.PaymentData
import kotlinx.coroutines.flow.SharedFlow

sealed class RazorpayCheckoutEvent {
    /**
     * Emitted when Razorpay Checkout completes client-side.
     * IMPORTANT: Membership is NEVER activated here. ViewModel MUST send these fields
     * to POST /api/payment/verify on the secure backend for HMAC-SHA256 verification.
     */
    data class PaymentCapturedPendingVerification(
        val internalOrderId: String,
        val razorpayOrderId: String,
        val razorpaySubscriptionId: String = "",
        val razorpayPaymentId: String,
        val razorpaySignature: String,
        val paymentMethodLabel: String
    ) : RazorpayCheckoutEvent()

    data class PaymentCancelled(val internalOrderId: String, val message: String) : RazorpayCheckoutEvent()
    data class PaymentFailed(val internalOrderId: String, val code: Int, val message: String) : RazorpayCheckoutEvent()
}

/**
 * Compatibility facade delegating to [PaymentManager].
 */
object RazorpayCheckoutManager {
    val events: SharedFlow<RazorpayCheckoutEvent>
        get() = PaymentManager.events

    fun preload(context: Context) {
        PaymentManager(context.applicationContext).initialize()
    }

    fun openCheckout(
        activity: Activity,
        order: ServerOrderResponse,
        plan: MembershipPlan,
        method: PaymentMethodOption,
        customerEmail: String,
        customerName: String
    ): Result<Unit> = PaymentManager(activity.applicationContext).startPayment(
        activity = activity,
        order = order,
        plan = plan,
        method = method,
        customerEmail = customerEmail,
        customerName = customerName
    )

    fun dispatchPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        PaymentManager.handlePaymentSuccess(razorpayPaymentId, paymentData)
    }

    fun dispatchPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        PaymentManager.handlePaymentError(code, response, paymentData)
    }
}
