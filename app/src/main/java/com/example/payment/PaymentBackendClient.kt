package com.example.payment

import com.example.BuildConfig
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Secure HTTPS client communicating with the PaperPilot Node.js payment backend.
 * Every request carries the signed-in user's Firebase ID Token (`Authorization: Bearer <token>`).
 * Private payment secrets (RAZORPAY_KEY_SECRET, webhook secrets, Play service account) NEVER exist in the APK.
 */
class PaymentBackendClient(
    private val baseUrl: String = BuildConfig.PAYMENT_BACKEND_BASE_URL.trim().trimEnd('/')
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = baseUrl.startsWith("https://") || baseUrl.startsWith("http://10.0.2.2")

    private fun requireBaseUrl(): String {
        if (!isConfigured()) {
            throw IllegalStateException(
                "Payment backend URL is not configured yet. Deploy /backend/ (Node.js) and set PAYMENT_BACKEND_BASE_URL in app/build.gradle.kts. Membership is never activated without server-side verification."
            )
        }
        return baseUrl
    }

    suspend fun createOrder(
        firebaseIdToken: String,
        plan: MembershipPlan,
        provider: PaymentGatewayProvider,
        idempotencyKey: String
    ): Result<ServerOrderResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/payment/create-order"
            val payload = JSONObject().apply {
                put("planTier", plan.tierId)
                put("paymentProvider", provider.id)
                put("idempotencyKey", idempotencyKey)
            }
            val json = executePost(url, firebaseIdToken, payload)
            ServerOrderResponse(
                orderId = json.getString("orderId"),
                razorpayOrderId = json.optString("razorpayOrderId", ""),
                razorpayKeyId = json.optString("razorpayKeyId", BuildConfig.RAZORPAY_PUBLIC_KEY_ID),
                planTier = json.getString("planTier"),
                amountInr = json.getLong("amountInr"),
                amountPaise = json.getLong("amountPaise"),
                currency = json.optString("currency", "INR"),
                duplicate = json.optBoolean("duplicate", false)
            )
        }
    }

    suspend fun createTrialAutoPayOrder(
        firebaseIdToken: String,
        autoPayTargetPlan: MembershipPlan,
        idempotencyKey: String
    ): Result<ServerOrderResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/subscription/trial-autopay"
            val payload = JSONObject().apply {
                put("autoPayTargetPlan", autoPayTargetPlan.tierId)
                put("idempotencyKey", idempotencyKey)
            }
            val json = executePost(url, firebaseIdToken, payload)
            ServerOrderResponse(
                orderId = json.getString("orderId"),
                razorpayOrderId = json.optString("razorpayOrderId", ""),
                razorpayKeyId = json.optString("razorpayKeyId", BuildConfig.RAZORPAY_PUBLIC_KEY_ID),
                planTier = json.optString("planTier", MembershipPlan.TRIAL_1DAY.tierId),
                amountInr = json.optLong("amountInr", 1L),
                amountPaise = json.optLong("amountPaise", 100L),
                currency = json.optString("currency", "INR"),
                duplicate = json.optBoolean("duplicate", false),
                razorpaySubscriptionId = json.optString("razorpaySubscriptionId", ""),
                autoPayEnabled = json.optBoolean("autoPayEnabled", true),
                autoPayTargetPlan = json.optString("autoPayTargetPlan", autoPayTargetPlan.tierId),
                nextChargeAmountInr = json.optLong("nextChargeAmountInr", autoPayTargetPlan.priceInr)
            )
        }
    }

    suspend fun verifyRazorpayPayment(
        firebaseIdToken: String,
        orderId: String,
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
        paymentMethodLabel: String,
        razorpaySubscriptionId: String = ""
    ): Result<VerifiedPaymentOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/payment/verify"
            val payload = JSONObject().apply {
                put("orderId", orderId)
                put("razorpayOrderId", razorpayOrderId)
                if (razorpaySubscriptionId.isNotBlank()) {
                    put("razorpaySubscriptionId", razorpaySubscriptionId)
                }
                put("razorpayPaymentId", razorpayPaymentId)
                put("razorpaySignature", razorpaySignature)
                put("paymentMethod", paymentMethodLabel)
            }
            val json = executePost(url, firebaseIdToken, payload)
            parseVerifiedOutcome(json)
        }
    }

    suspend fun setAutoPayEnabled(
        firebaseIdToken: String,
        enabled: Boolean,
        reason: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/subscription/autopay/toggle"
            val payload = JSONObject().apply {
                put("enabled", enabled)
                put("reason", reason.take(200))
            }
            executePost(url, firebaseIdToken, payload)
            Unit
        }
    }

    suspend fun cancelOrder(
        firebaseIdToken: String,
        orderId: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (orderId.isBlank()) return@runCatching
            val url = "${requireBaseUrl()}/api/payment/cancel-order"
            val payload = JSONObject().apply {
                put("orderId", orderId)
                put("reason", reason.take(200))
            }
            executePost(url, firebaseIdToken, payload)
            Unit
        }
    }

    suspend fun verifyGooglePlayPurchase(
        firebaseIdToken: String,
        productId: String,
        purchaseToken: String,
        orderId: String
    ): Result<VerifiedPaymentOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/payments/google-play/verify"
            val payload = JSONObject().apply {
                put("productId", productId)
                put("purchaseToken", purchaseToken)
                put("orderId", orderId)
            }
            val json = executePost(url, firebaseIdToken, payload)
            parseVerifiedOutcome(json)
        }
    }

    suspend fun cancelSubscription(
        firebaseIdToken: String,
        reason: String,
        immediate: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/subscription/cancel"
            val payload = JSONObject().apply {
                put("reason", reason.take(200))
                put("immediate", immediate)
            }
            executePost(url, firebaseIdToken, payload)
            Unit
        }
    }

    suspend fun getMembershipStatus(firebaseIdToken: String): Result<ServerMembershipStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/membership/status"
            val json = executeGet(url, firebaseIdToken)
            val mJson = json.optJSONObject("membership")
            ServerMembershipStatus(
                isPremiumActive = json.optBoolean("isPremiumActive", false),
                effectiveStatus = json.optString("effectiveStatus", "NONE"),
                adsEnabled = json.optBoolean("adsEnabled", true),
                planTier = json.optString("planTier", "FREE"),
                expiryDateMs = mJson?.optLong("expiryDateMs", 0L) ?: 0L,
                autoPayEnabled = mJson?.optBoolean("autoPayEnabled", false) ?: false,
                autoPayTargetPlan = mJson?.optString("autoPayTargetPlan", "").orEmpty(),
                autoPayMandateId = mJson?.optString("autoPayMandateId", "").orEmpty(),
                nextBillingAmountInr = mJson?.optLong("nextBillingAmountInr", 0L) ?: 0L,
                isTrial = mJson?.optBoolean("isTrial", false) ?: false,
                hasUsedTrial = mJson?.optBoolean("hasUsedTrial", false) ?: false
            )
        }
    }

    suspend fun getPaymentHistory(firebaseIdToken: String): Result<List<PaymentTransaction>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/payment/history"
            val json = executeGet(url, firebaseIdToken)
            val arr = json.optJSONArray("payments")
            val list = mutableListOf<PaymentTransaction>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseTransaction(obj))
                }
            }
            list
        }
    }

    suspend fun restoreSubscription(firebaseIdToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/membership/status"
            val json = executeGet(url, firebaseIdToken)
            json.optBoolean("isPremiumActive", false)
        }
    }

    suspend fun processAdminRefund(
        firebaseIdToken: String,
        paymentId: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${requireBaseUrl()}/api/admin/refunds"
            val payload = JSONObject().apply {
                put("paymentId", paymentId)
                put("reason", reason.take(200))
            }
            executePost(url, firebaseIdToken, payload)
            Unit
        }
    }

    private fun executeGet(url: String, idToken: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $idToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val errMsg = parsed.optString("error").ifBlank { "Server returned HTTP ${response.code}" }
                throw IllegalStateException(errMsg)
            }
            return parsed
        }
    }

    private fun executePost(url: String, idToken: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val errMsg = parsed.optString("error").ifBlank { "Server returned HTTP ${response.code}" }
                throw IllegalStateException(errMsg)
            }
            return parsed
        }
    }

    private fun parseTransaction(it: JSONObject): PaymentTransaction = PaymentTransaction(
        id = it.optString("paymentId"),
        paymentId = it.optString("paymentId"),
        userId = it.optString("userId"),
        orderId = it.optString("orderId"),
        transactionId = it.optString("transactionId"),
        planTier = it.optString("planTier"),
        amountInr = it.optLong("amountInr"),
        currency = it.optString("currency", "INR"),
        paymentProvider = it.optString("paymentProvider"),
        paymentMethod = it.optString("paymentMethod"),
        status = it.optString("status"),
        purchaseDateMs = it.optLong("purchaseDateMs"),
        expiryDateMs = it.optLong("expiryDateMs"),
        receiptNumber = it.optString("receiptNumber"),
        failureReason = it.optString("failureReason")
    )

    private fun parseVerifiedOutcome(json: JSONObject): VerifiedPaymentOutcome {
        val verified = json.optBoolean("verified", false)
        val duplicate = json.optBoolean("duplicate", false)
        val pJson = json.optJSONObject("payment")
        val mJson = json.optJSONObject("membership")
        val tx = pJson?.let(::parseTransaction)
        return VerifiedPaymentOutcome(
            verified = verified,
            duplicate = duplicate,
            transaction = tx,
            expiryDateMs = mJson?.optLong("expiryDateMs") ?: tx?.expiryDateMs ?: 0L,
            errorMessage = if (verified) null else json.optString("error", "Payment verification failed.")
        )
    }
}
