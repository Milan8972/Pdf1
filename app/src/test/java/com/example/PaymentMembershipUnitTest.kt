package com.example

import com.example.ads.AdMobManager
import com.example.auth.MembershipPlan
import com.example.auth.UserMembership
import com.example.payment.PaymentBackendClient
import com.example.payment.PaymentGatewayProvider
import com.example.payment.PaymentManager
import com.example.payment.PaymentMethodOption
import com.example.payment.PaymentScreenStep
import com.example.payment.RazorpayCheckoutEvent
import com.razorpay.Checkout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests verifying PaperPilot Premium pricing (₹99/month, ₹999/year),
 * server-only payment activation guard, AdMob suppression/restoration across
 * active, expired, cancelled, and refunded membership states, and Play Billing SKUs.
 */
class PaymentMembershipUnitTest {

    @Test
    fun pricingCatalogContainsTrial1DayAndPaidMonthly99Yearly999() {
        val allPlans = MembershipPlan.entries
        assertEquals(3, allPlans.size)
        assertEquals(listOf(MembershipPlan.MONTHLY, MembershipPlan.YEARLY), MembershipPlan.paidPlans)

        val trial = MembershipPlan.TRIAL_1DAY
        assertEquals("TRIAL_1DAY", trial.tierId)
        assertEquals(1L, trial.priceInr)
        assertEquals(100L, trial.amountPaise)
        assertEquals(1L, trial.durationDays)
        assertTrue(trial.isTrial)

        val monthly = MembershipPlan.MONTHLY
        assertEquals("MONTHLY_99", monthly.tierId)
        assertEquals(99L, monthly.priceInr)
        assertEquals(9900L, monthly.amountPaise)
        assertEquals(30L, monthly.durationDays)
        assertEquals("paperpilot_premium_monthly", monthly.playProductId)

        val yearly = MembershipPlan.YEARLY
        assertEquals("YEARLY_999", yearly.tierId)
        assertEquals(999L, yearly.priceInr)
        assertEquals(99900L, yearly.amountPaise)
        assertEquals(365L, yearly.durationDays)
        assertEquals("paperpilot_premium_yearly", yearly.playProductId)

        // Ensure old ₹499 / ₹100 / ₹1100 / lifetime tiers cannot resolve
        assertNull(MembershipPlan.fromTierId("MONTHLY_100"))
        assertNull(MembershipPlan.fromTierId("YEARLY_1100"))
        assertNull(MembershipPlan.fromTierId("YEARLY_499"))
        assertNull(MembershipPlan.fromTierId("LIFETIME_999"))
    }

    @Test
    fun activeMonthlyAndYearlyMembershipSuppressesAllAdMobAds() {
        val futureMs = System.currentTimeMillis() + 30L * 86_400_000L
        val activeMonthly = UserMembership(
            id = "uid_1",
            userId = "uid_1",
            email = "member@gmail.com",
            planTier = MembershipPlan.MONTHLY.tierId,
            priceInr = 99L,
            membershipStatus = "ACTIVE",
            premiumUntilEpochMs = futureMs
        )
        assertTrue(activeMonthly.hasActivePremium)
        assertEquals(MembershipPlan.MONTHLY, activeMonthly.activePlan)

        AdMobManager.setPremiumActive(activeMonthly.hasActivePremium)
        assertTrue(AdMobManager.areAdsSuppressed())

        val activeYearly = activeMonthly.copy(
            planTier = MembershipPlan.YEARLY.tierId,
            priceInr = 999L,
            premiumUntilEpochMs = System.currentTimeMillis() + 365L * 86_400_000L
        )
        assertTrue(activeYearly.hasActivePremium)
        assertEquals(MembershipPlan.YEARLY, activeYearly.activePlan)
        AdMobManager.setPremiumActive(activeYearly.hasActivePremium)
        assertTrue(AdMobManager.areAdsSuppressed())
    }

    @Test
    fun expiredOrRefundedMembershipReturnsToFreeAndRestoresAdMobAds() {
        val pastMs = System.currentTimeMillis() - 60_000L
        val expired = UserMembership(
            id = "uid_exp",
            userId = "uid_exp",
            planTier = MembershipPlan.MONTHLY.tierId,
            priceInr = 99L,
            membershipStatus = "ACTIVE",
            premiumUntilEpochMs = pastMs
        )
        assertFalse(expired.hasActivePremium)
        assertEquals("EXPIRED", expired.effectiveStatus)
        assertNull(expired.activePlan)

        AdMobManager.setPremiumActive(expired.hasActivePremium)
        assertFalse(AdMobManager.areAdsSuppressed())

        val refunded = UserMembership(
            id = "uid_ref",
            userId = "uid_ref",
            planTier = MembershipPlan.YEARLY.tierId,
            priceInr = 999L,
            membershipStatus = "REFUNDED",
            premiumUntilEpochMs = System.currentTimeMillis() + 86_400_000L
        )
        assertFalse(refunded.hasActivePremium)
        assertEquals("REFUNDED", refunded.effectiveStatus)

        AdMobManager.setPremiumActive(refunded.hasActivePremium)
        assertFalse(AdMobManager.areAdsSuppressed())
    }

    @Test
    fun unconfiguredBackendNeverFakesPaymentSuccess() = runTest {
        val client = PaymentBackendClient(baseUrl = "")
        assertFalse(client.isConfigured())

        val orderResult = client.createOrder(
            firebaseIdToken = "fake_token",
            plan = MembershipPlan.MONTHLY,
            provider = PaymentGatewayProvider.RAZORPAY,
            idempotencyKey = "idem_1"
        )
        assertTrue(orderResult.isFailure)
        assertNotNull(orderResult.exceptionOrNull()?.message)
    }

    @Test
    fun allTenPaymentScreensAndPaymentMethodsAreDefined() {
        val requiredSteps = listOf(
            PaymentScreenStep.PREMIUM_MEMBERSHIP,
            PaymentScreenStep.SELECT_PLAN,
            PaymentScreenStep.PAYMENT_METHOD,
            PaymentScreenStep.ORDER_SUMMARY,
            PaymentScreenStep.PAYMENT_PROCESSING,
            PaymentScreenStep.PAYMENT_SUCCESSFUL,
            PaymentScreenStep.PAYMENT_FAILED,
            PaymentScreenStep.TRANSACTION_HISTORY,
            PaymentScreenStep.MEMBERSHIP_DETAILS,
            PaymentScreenStep.CANCEL_SUBSCRIPTION,
            PaymentScreenStep.ADMIN_DASHBOARD
        )
        assertEquals(11, requiredSteps.size)

        // Primary Google Play Billing + India Razorpay methods (UPI, GPay, PhonePe, Debit, Credit, NetBanking)
        assertTrue(PaymentMethodOption.GOOGLE_PLAY_BILLING.isPrimaryPlayCompliant)
        assertEquals(6, PaymentMethodOption.entries.count { it.provider == PaymentGatewayProvider.RAZORPAY })
    }

    @Test
    fun paymentManagerValidatesPublicKeyAndRejectsBlankSignatureCallbacks() = runTest {
        // 1. Key validation rejects blank and placeholder keys, accepts valid test/live public key IDs
        assertTrue(runCatching { PaymentManager.validatePublicKey("") }.isFailure)
        assertTrue(runCatching { PaymentManager.validatePublicKey("rzp_test_replace_with_your_key_id") }.isFailure)
        assertEquals("rzp_test_validkey123", PaymentManager.validatePublicKey("  rzp_test_validkey123  "))

        // 2. Blank signature MUST NOT emit PaymentCapturedPendingVerification
        var capturedEvent: RazorpayCheckoutEvent? = null
        val job1 = launch {
            capturedEvent = PaymentManager.events.first()
        }
        testScheduler.runCurrent()
        PaymentManager.handlePaymentSuccess(razorpayPaymentId = "pay_test_123", paymentData = null)
        job1.join()
        assertTrue(capturedEvent is RazorpayCheckoutEvent.PaymentFailed)

        // 3. User cancellation emits PaymentCancelled (keeps account Free with ads)
        var cancelEvent: RazorpayCheckoutEvent? = null
        val job2 = launch {
            cancelEvent = PaymentManager.events.first()
        }
        testScheduler.runCurrent()
        PaymentManager.handlePaymentError(
            code = Checkout.PAYMENT_CANCELED,
            response = "User closed Razorpay sheet",
            paymentData = null
        )
        job2.join()
        assertTrue(cancelEvent is RazorpayCheckoutEvent.PaymentCancelled)

        // 4. Payment failure emits PaymentFailed
        var failEvent: RazorpayCheckoutEvent? = null
        val job3 = launch {
            failEvent = PaymentManager.events.first()
        }
        testScheduler.runCurrent()
        PaymentManager.handlePaymentError(
            code = Checkout.NETWORK_ERROR,
            response = "Network timeout during UPI authorization",
            paymentData = null
        )
        job3.join()
        assertTrue(failEvent is RazorpayCheckoutEvent.PaymentFailed)
    }

    @Test
    fun unconfiguredBackendBlocksVerifyStatusAndHistoryRequests() = runTest {
        val client = PaymentBackendClient(baseUrl = "")
        assertTrue(
            client.verifyRazorpayPayment(
                firebaseIdToken = "tok",
                orderId = "ord_1",
                razorpayOrderId = "order_1",
                razorpayPaymentId = "pay_1",
                razorpaySignature = "sig",
                paymentMethodLabel = "UPI"
            ).isFailure
        )
        assertTrue(client.getMembershipStatus("tok").isFailure)
        assertTrue(client.getPaymentHistory("tok").isFailure)
        assertTrue(
            client.createTrialAutoPayOrder(
                firebaseIdToken = "tok",
                autoPayTargetPlan = MembershipPlan.MONTHLY,
                idempotencyKey = "idem_trial"
            ).isFailure
        )
        assertTrue(client.setAutoPayEnabled(firebaseIdToken = "tok", enabled = false).isFailure)
    }

    @Test
    fun oneRupeeTrialSuppressesAdsAndRestoresAdsAfter24hExpiry() {
        val activeTrial = UserMembership(
            id = "uid_trial",
            userId = "uid_trial",
            email = "trial@gmail.com",
            planTier = MembershipPlan.TRIAL_1DAY.tierId,
            priceInr = 1L,
            membershipStatus = "ACTIVE",
            premiumUntilEpochMs = System.currentTimeMillis() + 86_400_000L,
            autoPayEnabled = true,
            autoPayTargetPlan = MembershipPlan.MONTHLY.tierId,
            nextBillingAmountInr = 99L,
            isTrial = true,
            hasUsedTrial = true
        )
        assertTrue(activeTrial.hasActivePremium)
        assertEquals(MembershipPlan.TRIAL_1DAY, activeTrial.activePlan)
        AdMobManager.setPremiumActive(activeTrial.hasActivePremium)
        assertTrue(AdMobManager.areAdsSuppressed())

        // After 24h trial expires without renewal, user returns to Free with ads restored
        val expiredTrial = activeTrial.copy(
            premiumUntilEpochMs = System.currentTimeMillis() - 10_000L,
            autoPayEnabled = false
        )
        assertFalse(expiredTrial.hasActivePremium)
        assertEquals("EXPIRED", expiredTrial.effectiveStatus)
        assertNull(expiredTrial.activePlan)
        AdMobManager.setPremiumActive(expiredTrial.hasActivePremium)
        assertFalse(AdMobManager.areAdsSuppressed())
    }
}
