package com.example.auth

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

/**
 * Authoritative PaperPilot Premium Membership Plans:
 * - Monthly Premium: ₹99/month
 * - Yearly Premium: ₹999/year
 * No ₹499 yearly or ₹999 lifetime plans exist.
 */
enum class MembershipPlan(
    val tierId: String,
    val title: String,
    val priceInr: Long,
    val amountPaise: Long,
    val durationDays: Long,
    val priceDisplay: String,
    val billingPeriod: String,
    val playProductId: String,
    val badge: String,
    val isTrial: Boolean = false,
    val features: List<String>
) {
    TRIAL_1DAY(
        tierId = "TRIAL_1DAY",
        title = "1-Day Trial + AutoPay",
        priceInr = 1L,
        amountPaise = 100L,
        durationDays = 1L,
        priceDisplay = "₹1",
        billingPeriod = "1 day",
        playProductId = "paperpilot_premium_monthly",
        badge = "₹1 TRIAL • 24H PRO",
        isTrial = true,
        features = listOf(
            "Full 24-hour Ad-free PaperPilot Premium trial for ₹1",
            "Unlock all PDF editing, OCR, signing & conversion tools",
            "UPI AutoPay mandate setup (renews at ₹99/mo or ₹999/yr after 1 day)",
            "Cancel AutoPay anytime before trial ends with zero renewal charge"
        )
    ),
    MONTHLY(
        tierId = "MONTHLY_99",
        title = "Monthly Premium",
        priceInr = 99L,
        amountPaise = 9900L,
        durationDays = 30L,
        priceDisplay = "₹99",
        billingPeriod = "month",
        playProductId = "paperpilot_premium_monthly",
        badge = "Flexible Monthly",
        features = listOf(
            "100% Ad-free experience (all AdMob formats disabled)",
            "Premium PDF tools unlocked",
            "Advanced PDF editing & annotations",
            "Full PDF conversion tools (Word, Excel, PPT, Images)",
            "Unlimited operations (subject to reasonable fair-use limits)",
            "Verified Premium membership badge"
        )
    ),
    YEARLY(
        tierId = "YEARLY_999",
        title = "Yearly Premium",
        priceInr = 999L,
        amountPaise = 99900L,
        durationDays = 365L,
        priceDisplay = "₹999",
        billingPeriod = "year",
        playProductId = "paperpilot_premium_yearly",
        badge = "Best Value • Save ₹189",
        features = listOf(
            "All Monthly Premium features included",
            "100% Ad-free experience all year",
            "Advanced PDF tools & batch workflows",
            "Priority customer support",
            "Automatic yearly renewal where supported"
        )
    );

    companion object {
        val paidPlans: List<MembershipPlan> = listOf(MONTHLY, YEARLY)
        fun fromTierId(raw: String?): MembershipPlan? = entries.firstOrNull { it.tierId == raw }
        fun fromPlayProductId(productId: String?): MembershipPlan? =
            paidPlans.firstOrNull { it.playProductId == productId }
    }
}

@Keep
@IgnoreExtraProperties
data class UserMembership(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val planTier: String = "FREE",
    val priceInr: Long = 0L,
    val currency: String = "INR",
    val membershipStatus: String = "NONE",
    val premiumUntilEpochMs: Long = 0L,
    val autoRenew: Boolean = false,
    val autoPayEnabled: Boolean = false,
    val autoPayTargetPlan: String = "",
    val autoPayMandateId: String = "",
    val nextBillingAmountInr: Long = 0L,
    val isTrial: Boolean = false,
    val hasUsedTrial: Boolean = false,
    val paymentProvider: String = "",
    val lastTransactionId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val hasActivePremium: Boolean
        get() = (membershipStatus == "ACTIVE" || membershipStatus == "CANCELLED") &&
            (planTier == MembershipPlan.TRIAL_1DAY.tierId ||
                planTier == MembershipPlan.MONTHLY.tierId ||
                planTier == MembershipPlan.YEARLY.tierId) &&
            premiumUntilEpochMs > System.currentTimeMillis()

    @get:Exclude
    val activePlan: MembershipPlan?
        get() = if (hasActivePremium) MembershipPlan.fromTierId(planTier) else null

    @get:Exclude
    val effectiveStatus: String
        get() = when {
            membershipStatus == "REFUNDED" -> "REFUNDED"
            (membershipStatus == "ACTIVE" || membershipStatus == "CANCELLED") &&
                premiumUntilEpochMs in 1..System.currentTimeMillis() -> "EXPIRED"
            else -> membershipStatus
        }
}

@Keep
@IgnoreExtraProperties
data class PaymentTransaction(
    @DocumentId val id: String = "",
    val paymentId: String = "",
    val userId: String = "",
    val orderId: String = "",
    val transactionId: String = "",
    val planTier: String = "",
    val amountInr: Long = 0L,
    val currency: String = "INR",
    val paymentProvider: String = "",
    val paymentMethod: String = "",
    val status: String = "",
    val purchaseDateMs: Long = 0L,
    val expiryDateMs: Long = 0L,
    val receiptNumber: String = "",
    val failureReason: String = ""
)

@Keep
@IgnoreExtraProperties
data class RefundRecord(
    @DocumentId val id: String = "",
    val refundId: String = "",
    val userId: String = "",
    val paymentId: String = "",
    val transactionId: String = "",
    val amountInr: Long = 0L,
    val currency: String = "INR",
    val paymentProvider: String = "",
    val reason: String = "",
    val status: String = "",
    val processedAtMs: Long = 0L
)

data class AdminDashboardMetrics(
    val totalRegisteredUsers: Int = 0,
    val totalPremiumMembers: Int = 0,
    val activeMonthlyMembers: Int = 0,
    val activeYearlyMembers: Int = 0,
    val expiredMemberships: Int = 0,
    val successfulPayments: Int = 0,
    val failedTransactions: Int = 0,
    val refundsCount: Int = 0,
    val totalGrossRevenueInr: Long = 0L,
    val monthlyRevenueInr: Long = 0L,
    val yearlyRevenueInr: Long = 0L,
    val recentTransactions: List<PaymentTransaction> = emptyList(),
    val recentRefunds: List<RefundRecord> = emptyList()
)

class FirebaseMembershipRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth = Firebase.auth

    companion object {
        private const val PRIMARY_ADMIN_EMAIL = "milanmoondal@gmail.com"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeEmail(raw: String): String {
        val clean = raw.trim().take(254)
        return if (clean.length >= 3) clean else "user@gmail.com"
    }

    private fun sanitizeDisplayName(raw: String): String = raw.trim().take(100)

    /**
     * Checks if the currently signed-in Google account is an authorized PaperPilot Administrator.
     */
    suspend fun checkIsAdmin(uid: String, email: String): Boolean {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return false
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && email.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            val tokenResult = user.getIdToken(false).await()
            if (tokenResult.claims["admin"] == true) return true
            val adminSnap = db.collection("admins").document(uid).get().await()
            adminSnap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun observeMembership(uid: String): Flow<UserMembership?> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        null
                    } else {
                        snapshot.toObject(
                            UserMembership::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserPayments(uid: String): Flow<List<PaymentTransaction>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val path = "payments"
        emitAll(
            db.collection("payments")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        doc.toObject(PaymentTransaction::class.java)
                    }.sortedByDescending { it.purchaseDateMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    emit(emptyList())
                }
        )
    }

    fun observeUserRefunds(uid: String): Flow<List<RefundRecord>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("refunds")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { it.toObject(RefundRecord::class.java) }
                        .sortedByDescending { it.processedAtMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    /**
     * Initializes a FREE user profile in /users/{uid} (or refreshes contact email/displayName).
     * Paid membership fields are NEVER modified client-side; only the verified payment backend
     * writes paid entitlements via Firebase Admin SDK.
     */
    suspend fun ensureUserProfile(email: String, displayName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val safeEmail = sanitizeEmail(email)
            val safeName = sanitizeDisplayName(displayName)

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to safeEmail,
                    "displayName" to safeName,
                    "planTier" to "FREE",
                    "priceInr" to 0L,
                    "currency" to "INR",
                    "membershipStatus" to "NONE",
                    "premiumUntilEpochMs" to 0L,
                    "autoRenew" to false,
                    "paymentProvider" to "",
                    "lastTransactionId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "email" to safeEmail,
                        "displayName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Admin-only snapshot of all 5 collections computing the 11 required Admin Dashboard KPIs.
     * Protected by both Firebase Auth admin verification and Firestore security rules.
     */
    suspend fun loadAdminDashboardSnapshot(): Result<AdminDashboardMetrics> {
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email.orEmpty()
            if (!checkIsAdmin(uid, email)) {
                return Result.failure(SecurityException("Administrator authorization required."))
            }

            val usersSnap = db.collection("users").get().await()
            val membershipsSnap = db.collection("memberships").get().await()
            val paymentsSnap = db.collection("payments").get().await()
            val refundsSnap = db.collection("refunds").get().await()

            val nowMs = System.currentTimeMillis()
            var activeMonthly = 0
            var activeYearly = 0
            var expiredCount = 0

            membershipsSnap.documents.forEach { doc ->
                val tier = doc.getString("planTier").orEmpty()
                val status = doc.getString("status").orEmpty()
                val expiry = doc.getLong("expiryDateMs") ?: 0L
                val isActive = (status == "ACTIVE" || status == "CANCELLED") && expiry > nowMs
                if (isActive) {
                    if (tier == MembershipPlan.MONTHLY.tierId) activeMonthly++
                    else if (tier == MembershipPlan.YEARLY.tierId) activeYearly++
                } else if (status == "EXPIRED" || (expiry in 1..nowMs && status != "REFUNDED")) {
                    expiredCount++
                }
            }

            val payments = paymentsSnap.documents.mapNotNull { it.toObject(PaymentTransaction::class.java) }
                .sortedByDescending { it.purchaseDateMs }
            val refunds = refundsSnap.documents.mapNotNull { it.toObject(RefundRecord::class.java) }
                .sortedByDescending { it.processedAtMs }

            var successCount = 0
            var failedCount = 0
            var monthlyRev = 0L
            var yearlyRev = 0L

            payments.forEach { p ->
                when (p.status) {
                    "SUCCESS" -> {
                        successCount++
                        if (p.planTier == MembershipPlan.MONTHLY.tierId) monthlyRev += p.amountInr
                        if (p.planTier == MembershipPlan.YEARLY.tierId) yearlyRev += p.amountInr
                    }
                    "FAILED" -> failedCount++
                }
            }

            Result.success(
                AdminDashboardMetrics(
                    totalRegisteredUsers = usersSnap.size(),
                    totalPremiumMembers = activeMonthly + activeYearly,
                    activeMonthlyMembers = activeMonthly,
                    activeYearlyMembers = activeYearly,
                    expiredMemberships = expiredCount,
                    successfulPayments = successCount,
                    failedTransactions = failedCount,
                    refundsCount = refunds.size,
                    totalGrossRevenueInr = monthlyRev + yearlyRev,
                    monthlyRevenueInr = monthlyRev,
                    yearlyRevenueInr = yearlyRev,
                    recentTransactions = payments.take(50),
                    recentRefunds = refunds.take(50)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "admin_dashboard")
            Result.failure(e)
        }
    }
}
