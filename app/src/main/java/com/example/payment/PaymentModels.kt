package com.example.payment

import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction

enum class PaymentGatewayProvider(val id: String, val label: String) {
    GOOGLE_PLAY("GOOGLE_PLAY", "Google Play Billing"),
    RAZORPAY("RAZORPAY", "Razorpay (India Alternative Billing)")
}

enum class PaymentMethodOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val provider: PaymentGatewayProvider,
    val razorpayMethodCode: String,
    val isPrimaryPlayCompliant: Boolean = false
) {
    GOOGLE_PLAY_BILLING(
        id = "google_play_billing",
        title = "Google Play Billing",
        subtitle = "Official Play Store subscriptions (UPI, Cards, Play Balance)",
        provider = PaymentGatewayProvider.GOOGLE_PLAY,
        razorpayMethodCode = "",
        isPrimaryPlayCompliant = true
    ),
    RAZORPAY_UPI(
        id = "razorpay_upi",
        title = "UPI (BHIM / Any UPI App)",
        subtitle = "Instant UPI payment via Razorpay • India Alternative Billing",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "upi"
    ),
    RAZORPAY_GPAY(
        id = "razorpay_gpay",
        title = "Google Pay (UPI)",
        subtitle = "Authorized Google Pay UPI flow via Razorpay",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "upi"
    ),
    RAZORPAY_PHONEPE(
        id = "razorpay_phonepe",
        title = "PhonePe (UPI)",
        subtitle = "Authorized PhonePe UPI flow via Razorpay",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "upi"
    ),
    RAZORPAY_DEBIT_CARD(
        id = "razorpay_debit_card",
        title = "Debit Card",
        subtitle = "Visa, Mastercard, RuPay via Razorpay 3DS",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "card"
    ),
    RAZORPAY_CREDIT_CARD(
        id = "razorpay_credit_card",
        title = "Credit Card",
        subtitle = "All major Indian & international credit cards",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "card"
    ),
    RAZORPAY_NET_BANKING(
        id = "razorpay_net_banking",
        title = "Net Banking",
        subtitle = "SBI, HDFC, ICICI, Axis & 50+ Indian banks",
        provider = PaymentGatewayProvider.RAZORPAY,
        razorpayMethodCode = "netbanking"
    )
}

/**
 * All 10 required PaperPilot Payment & Membership screens + Admin Dashboard.
 */
enum class PaymentScreenStep {
    PREMIUM_MEMBERSHIP,
    SELECT_PLAN,
    PAYMENT_METHOD,
    ORDER_SUMMARY,
    PAYMENT_PROCESSING,
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    TRANSACTION_HISTORY,
    MEMBERSHIP_DETAILS,
    CANCEL_SUBSCRIPTION,
    ADMIN_DASHBOARD
}

data class ServerOrderResponse(
    val orderId: String,
    val razorpayOrderId: String,
    val razorpayKeyId: String,
    val planTier: String,
    val amountInr: Long,
    val amountPaise: Long,
    val currency: String,
    val duplicate: Boolean,
    val razorpaySubscriptionId: String = "",
    val autoPayEnabled: Boolean = false,
    val autoPayTargetPlan: String = "",
    val nextChargeAmountInr: Long = 0L
)

data class VerifiedPaymentOutcome(
    val verified: Boolean,
    val duplicate: Boolean,
    val transaction: PaymentTransaction?,
    val expiryDateMs: Long,
    val errorMessage: String? = null
)

data class ServerMembershipStatus(
    val isPremiumActive: Boolean,
    val effectiveStatus: String,
    val adsEnabled: Boolean,
    val planTier: String,
    val expiryDateMs: Long,
    val autoPayEnabled: Boolean = false,
    val autoPayTargetPlan: String = "",
    val autoPayMandateId: String = "",
    val nextBillingAmountInr: Long = 0L,
    val isTrial: Boolean = false,
    val hasUsedTrial: Boolean = false
)

data class PaymentFlowUiState(
    val currentStep: PaymentScreenStep = PaymentScreenStep.PREMIUM_MEMBERSHIP,
    val selectedPlan: MembershipPlan = MembershipPlan.YEARLY,
    val autoPayTargetPlan: MembershipPlan = MembershipPlan.MONTHLY,
    val selectedMethod: PaymentMethodOption = PaymentMethodOption.GOOGLE_PLAY_BILLING,
    val processingStage: String = "",
    val activeOrder: ServerOrderResponse? = null,
    val lastSuccessTransaction: PaymentTransaction? = null,
    val lastFailureTitle: String = "Payment Not Completed",
    val lastFailureReason: String = "",
    val isRestoringPurchases: Boolean = false,
    val isCancellingSubscription: Boolean = false,
    val isUpdatingAutoPay: Boolean = false,
    val playStorePrices: Map<MembershipPlan, String> = emptyMap(),
    val playBillingConnected: Boolean = false
)
