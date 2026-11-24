package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.data.DocumentSource
import com.example.data.PaperPilotDatabase
import com.example.data.ScannedImageEntity
import com.example.data.SettingsStore
import com.example.data.SignatureEntity
import com.example.data.UserSettings
import com.example.engine.DocumentTemplate
import com.example.engine.EditableTextBlock
import com.example.engine.EditorTool
import com.example.engine.PageAnnotation
import com.example.engine.PdfFlattener
import com.example.engine.PdfRenderHelper
import com.example.engine.SamplePdfGenerator
import com.example.engine.ToolEngine
import com.example.engine.ToolOptions
import com.example.engine.ToolOutputResult
import com.example.model.ToolId
import android.app.Activity
import com.example.ads.AdMobManager
import com.example.auth.AdminDashboardMetrics
import com.example.auth.FirebaseMembershipRepository
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import com.example.auth.RefundRecord
import com.example.auth.UserMembership
import com.example.payment.GooglePlayBillingManager
import com.example.payment.PaymentBackendClient
import com.example.payment.PaymentFlowUiState
import com.example.payment.PaymentGatewayProvider
import com.example.payment.PaymentManager
import com.example.payment.PaymentMethodOption
import com.example.payment.PaymentScreenStep
import com.example.payment.PlayBillingEvent
import com.example.payment.RazorpayCheckoutEvent
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val label: String) {
    HOME("Home"),
    FILES("Files"),
    TOOLS("Tools"),
    SCAN("Scan"),
    SETTINGS("Settings")
}

sealed class AppDestination {
    data object Onboarding : AppDestination()
    data object MainTabs : AppDestination()
    data class ToolFlow(val toolId: ToolId, val preselectedDocIds: List<Long> = emptyList()) : AppDestination()
    data class PdfViewer(val documentId: Long) : AppDestination()
    data class PdfEditor(val documentId: Long, val initialTool: EditorTool = EditorTool.EDIT_CONTENT) : AppDestination()
    data object ScannerCamera : AppDestination()
    data object TemplatesPicker : AppDestination()
    data object PremiumMembership : AppDestination()
}

data class MembershipUiState(
    val isSignedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val userId: String = "",
    val userEmail: String = "",
    val displayName: String = "",
    val isPremiumActive: Boolean = false,
    val activePlan: MembershipPlan? = null,
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
    val transactions: List<PaymentTransaction> = emptyList(),
    val refunds: List<RefundRecord> = emptyList(),
    val adminMetrics: AdminDashboardMetrics? = null,
    val isLoadingAdmin: Boolean = false,
    val adminError: String? = null,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class PaperPilotViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PaperPilotDatabase.getInstance(application)
    val repository = DocumentRepository(application, db)
    val settingsStore = SettingsStore(application)
    private val membershipRepository: FirebaseMembershipRepository? = runCatching { FirebaseMembershipRepository(application) }.getOrNull()
    private val paymentBackendClient = PaymentBackendClient()
    private val paymentManager = PaymentManager(application)
    private val playBillingManager = GooglePlayBillingManager(application)
    private val firebaseAuth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()

    private val _membershipState = MutableStateFlow(MembershipUiState())
    val membershipState: StateFlow<MembershipUiState> = _membershipState.asStateFlow()

    private val _paymentFlowState = MutableStateFlow(PaymentFlowUiState())
    val paymentFlowState: StateFlow<PaymentFlowUiState> = _paymentFlowState.asStateFlow()

    private var membershipObserverJob: Job? = null
    private var paymentsObserverJob: Job? = null
    private var refundsObserverJob: Job? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val trashedDocuments: StateFlow<List<DocumentEntity>> = repository.trashedDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val signatures: StateFlow<List<SignatureEntity>> = repository.allSignatures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scannedImages: StateFlow<List<ScannedImageEntity>> = repository.allScannedImages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _backStack = MutableStateFlow<List<AppDestination>>(listOf(AppDestination.MainTabs))
    val backStack: StateFlow<List<AppDestination>> = _backStack.asStateFlow()

    private val _isLoadingInitial = MutableStateFlow(true)
    val isLoadingInitial: StateFlow<Boolean> = _isLoadingInitial.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    // Pending password prompt when opening a protected document
    private val _passwordPromptDoc = MutableStateFlow<DocumentEntity?>(null)
    val passwordPromptDoc: StateFlow<DocumentEntity?> = _passwordPromptDoc.asStateFlow()

    private var activeToolJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSampleDocuments()
            _isLoadingInitial.value = false
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                if (!s.onboardingComplete && _backStack.value == listOf(AppDestination.MainTabs)) {
                    _backStack.value = listOf(AppDestination.MainTabs, AppDestination.Onboarding)
                }
            }
        }
        firebaseAuth?.let { authInstance ->
            val listener = FirebaseAuth.AuthStateListener { auth ->
                syncAuthAndMembership(auth)
            }
            authStateListener = listener
            authInstance.addAuthStateListener(listener)
            syncAuthAndMembership(authInstance)
        }

        // Initialize Google Play Billing v7 and observe live store prices + purchase updates
        playBillingManager.connect()
        viewModelScope.launch {
            playBillingManager.isConnected.collect { connected ->
                _paymentFlowState.value = _paymentFlowState.value.copy(playBillingConnected = connected)
            }
        }
        viewModelScope.launch {
            playBillingManager.productDetails.collect { detailsMap ->
                val formatted = detailsMap.mapValues { (_, details) ->
                    details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                        .orEmpty()
                }.filterValues { it.isNotBlank() }
                _paymentFlowState.value = _paymentFlowState.value.copy(playStorePrices = formatted)
            }
        }
        viewModelScope.launch {
            playBillingManager.events.collect { event ->
                handlePlayBillingEvent(event)
            }
        }
        viewModelScope.launch {
            paymentManager.events.collect { event ->
                handleRazorpayCheckoutEvent(event)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearMembershipListenerBeforeSignOut()
        authStateListener?.let { firebaseAuth?.removeAuthStateListener(it) }
        playBillingManager.endConnection()
    }

    private fun syncAuthAndMembership(auth: FirebaseAuth) {
        val user = auth.currentUser
        clearMembershipListenerBeforeSignOut()

        if (user == null || user.uid.isBlank()) {
            _membershipState.value = MembershipUiState()
            AdMobManager.setPremiumActive(false, getApplication())
            return
        }

        val uid = user.uid
        val email = user.email.orEmpty()
        val name = user.displayName.orEmpty()
        _membershipState.value = _membershipState.value.copy(
            isSignedIn = true,
            userId = uid,
            userEmail = email,
            displayName = name,
            errorMessage = null
        )

        val repo = membershipRepository ?: return

        viewModelScope.launch {
            val admin = repo.checkIsAdmin(uid, email)
            _membershipState.value = _membershipState.value.copy(isAdmin = admin)
        }

        membershipObserverJob = viewModelScope.launch {
            repo.observeMembership(uid)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    _membershipState.value = _membershipState.value.copy(
                        errorMessage = rootError.message ?: "Unable to sync Premium status."
                    )
                }
                .collect { doc: UserMembership? ->
                    val active = doc?.hasActivePremium == true
                    val prev = _membershipState.value
                    _membershipState.value = prev.copy(
                        isSignedIn = true,
                        userId = uid,
                        userEmail = doc?.email?.ifBlank { email } ?: email,
                        displayName = doc?.displayName?.ifBlank { name } ?: name,
                        isPremiumActive = active,
                        activePlan = doc?.activePlan,
                        membershipStatus = doc?.effectiveStatus ?: "NONE",
                        premiumUntilEpochMs = doc?.premiumUntilEpochMs ?: 0L,
                        autoRenew = doc?.autoRenew == true,
                        autoPayEnabled = doc?.autoPayEnabled == true,
                        autoPayTargetPlan = doc?.autoPayTargetPlan.orEmpty(),
                        autoPayMandateId = doc?.autoPayMandateId.orEmpty(),
                        nextBillingAmountInr = doc?.nextBillingAmountInr ?: 0L,
                        isTrial = doc?.isTrial == true && active,
                        hasUsedTrial = doc?.hasUsedTrial == true,
                        paymentProvider = doc?.paymentProvider.orEmpty(),
                        lastTransactionId = doc?.lastTransactionId.orEmpty(),
                        isMutating = false,
                        errorMessage = null
                    )
                    AdMobManager.setPremiumActive(active, getApplication())
                }
        }

        paymentsObserverJob = viewModelScope.launch {
            repo.observeUserPayments(uid).collect { txList ->
                _membershipState.value = _membershipState.value.copy(transactions = txList)
            }
        }

        refundsObserverJob = viewModelScope.launch {
            repo.observeUserRefunds(uid).collect { refundList ->
                _membershipState.value = _membershipState.value.copy(refunds = refundList)
            }
        }

        refreshMembershipStatusFromServer()
    }

    fun onGoogleAuthSucceeded() {
        val authInstance = firebaseAuth ?: return
        val user = authInstance.currentUser ?: return
        syncAuthAndMembership(authInstance)
        viewModelScope.launch {
            membershipRepository?.ensureUserProfile(
                email = user.email.orEmpty(),
                displayName = user.displayName.orEmpty()
            )?.onFailure { err ->
                _membershipState.value = _membershipState.value.copy(
                    errorMessage = err.localizedMessage ?: "Failed to sync Google account."
                )
            }
        }
        emitMessage("Signed in as ${user.email ?: "Google account"}")
    }

    fun clearMembershipListenerBeforeSignOut() {
        membershipObserverJob?.cancel()
        membershipObserverJob = null
        paymentsObserverJob?.cancel()
        paymentsObserverJob = null
        refundsObserverJob?.cancel()
        refundsObserverJob = null
    }

    fun onSignOutCompleted() {
        _membershipState.value = MembershipUiState()
        _paymentFlowState.value = PaymentFlowUiState()
        AdMobManager.setPremiumActive(false, getApplication())
        emitMessage("Signed out. Free ad-supported mode active.")
    }

    fun setPaymentStep(step: PaymentScreenStep) {
        if (step == PaymentScreenStep.ADMIN_DASHBOARD && !_membershipState.value.isAdmin) {
            emitMessage("Administrator authorization required.")
            return
        }
        _paymentFlowState.value = _paymentFlowState.value.copy(currentStep = step)
        if (step == PaymentScreenStep.ADMIN_DASHBOARD) {
            refreshAdminDashboard()
        }
    }

    fun selectMembershipPlan(plan: MembershipPlan) {
        _paymentFlowState.value = _paymentFlowState.value.copy(selectedPlan = plan)
    }

    fun selectPaymentMethod(method: PaymentMethodOption) {
        _paymentFlowState.value = _paymentFlowState.value.copy(selectedMethod = method)
    }

    fun selectAutoPayTargetPlan(plan: MembershipPlan) {
        val recurringTarget = if (plan == MembershipPlan.YEARLY) MembershipPlan.YEARLY else MembershipPlan.MONTHLY
        _paymentFlowState.value = _paymentFlowState.value.copy(autoPayTargetPlan = recurringTarget)
    }

    /**
     * Convenience entry point triggered directly from Monthly/Yearly pricing cards in
     * [com.example.ui.screens.PremiumMembershipScreen] using [PaymentManager].
     */
    fun startRazorpayOrderForPlan(
        activity: Activity?,
        plan: MembershipPlan,
        customPaymentManager: PaymentManager? = null
    ) {
        _paymentFlowState.value = _paymentFlowState.value.copy(
            selectedPlan = plan,
            selectedMethod = PaymentMethodOption.RAZORPAY_UPI
        )
        startVerifiedPaymentFlow(activity = activity, customPaymentManager = customPaymentManager)
    }

    /**
     * Initiates the ₹1 for 1-Day Trial + UPI AutoPay mandate setup flow via the secure backend
     * (`POST /api/subscription/trial-autopay`) and launches Razorpay Checkout via [PaymentManager].
     */
    fun startTrialAutoPayFlow(
        activity: Activity?,
        targetPlan: MembershipPlan = _paymentFlowState.value.autoPayTargetPlan,
        customPaymentManager: PaymentManager? = null
    ) {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            emitMessage("Please sign in with Google Mail to start the ₹1 1-day trial.")
            return
        }
        if (activity == null) {
            failPayment("Unable to Launch Razorpay", "Activity context unavailable.")
            return
        }
        val resolvedTarget = if (targetPlan == MembershipPlan.YEARLY) MembershipPlan.YEARLY else MembershipPlan.MONTHLY
        val activePaymentManager = customPaymentManager ?: paymentManager
        viewModelScope.launch {
            _paymentFlowState.value = _paymentFlowState.value.copy(
                selectedPlan = MembershipPlan.TRIAL_1DAY,
                autoPayTargetPlan = resolvedTarget,
                selectedMethod = PaymentMethodOption.RAZORPAY_UPI,
                currentStep = PaymentScreenStep.PAYMENT_PROCESSING,
                processingStage = "Creating ₹1 1-Day Trial + UPI AutoPay (${resolvedTarget.priceDisplay}/${resolvedTarget.billingPeriod}) order on server..."
            )
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            if (idToken.isBlank()) {
                failPayment("Authentication Required", "Could not retrieve verified Google auth token.")
                return@launch
            }
            val idempotencyKey = "${user.uid}_TRIAL_1DAY_${resolvedTarget.tierId}_${UUID.randomUUID()}"
            paymentBackendClient.createTrialAutoPayOrder(
                firebaseIdToken = idToken,
                autoPayTargetPlan = resolvedTarget,
                idempotencyKey = idempotencyKey
            ).onSuccess { order ->
                _paymentFlowState.value = _paymentFlowState.value.copy(
                    activeOrder = order,
                    processingStage = "Opening Razorpay UPI AutoPay checkout for ₹1 trial..."
                )
                activePaymentManager.startPayment(
                    activity = activity,
                    order = order,
                    plan = MembershipPlan.TRIAL_1DAY,
                    method = PaymentMethodOption.RAZORPAY_UPI,
                    customerEmail = user.email.orEmpty(),
                    customerName = user.displayName.orEmpty()
                ).onFailure { err ->
                    failPayment(
                        title = "Razorpay AutoPay Unavailable",
                        reason = err.message ?: "Unable to launch Razorpay Checkout SDK."
                    )
                }
            }.onFailure { err ->
                failPayment(
                    title = "Trial AutoPay Setup Failed",
                    reason = err.message ?: "Unable to connect to the secure payment backend."
                )
            }
        }
    }

    /**
     * Enables or disables automatic recurring UPI AutoPay billing on the backend without revoking
     * remaining paid or 24-hour trial access.
     */
    fun toggleAutoPay(enabled: Boolean, reason: String = "User updated AutoPay preference") {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            emitMessage("Sign in with Google Mail to manage AutoPay.")
            return
        }
        viewModelScope.launch {
            _paymentFlowState.value = _paymentFlowState.value.copy(isUpdatingAutoPay = true)
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            if (idToken.isBlank()) {
                _paymentFlowState.value = _paymentFlowState.value.copy(isUpdatingAutoPay = false)
                emitMessage("Unable to authenticate AutoPay update.")
                return@launch
            }
            paymentBackendClient.setAutoPayEnabled(
                firebaseIdToken = idToken,
                enabled = enabled,
                reason = reason
            ).onSuccess {
                _membershipState.value = _membershipState.value.copy(
                    autoPayEnabled = enabled,
                    autoRenew = enabled
                )
                emitMessage(
                    if (enabled) "UPI AutoPay enabled for automatic renewal."
                    else "AutoPay turned off. You won't be charged again; current access remains active until expiry."
                )
                refreshMembershipStatusFromServer()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not update AutoPay on server.")
            }
            _paymentFlowState.value = _paymentFlowState.value.copy(isUpdatingAutoPay = false)
        }
    }

    /**
     * Executes the 10-step server-verified payment workflow.
     * NEVER activates membership from an unverified Android callback.
     */
    fun startVerifiedPaymentFlow(
        activity: Activity?,
        customPaymentManager: PaymentManager? = null
    ) {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            emitMessage("Please sign in with Google Mail before purchasing Premium.")
            return
        }
        val state = _paymentFlowState.value
        val plan = state.selectedPlan
        val method = state.selectedMethod

        if (method.provider == PaymentGatewayProvider.GOOGLE_PLAY) {
            if (activity == null) {
                failPayment("Unable to Launch Play Billing", "Activity context unavailable.")
                return
            }
            _paymentFlowState.value = state.copy(
                currentStep = PaymentScreenStep.PAYMENT_PROCESSING,
                processingStage = "Opening official Google Play Billing for ${plan.title} (${plan.priceDisplay}/${plan.billingPeriod})..."
            )
            playBillingManager.launchSubscriptionFlow(activity, plan).onFailure { err ->
                failPayment(
                    title = "Google Play Billing Unavailable",
                    reason = err.message ?: "Configure subscription products in Play Console and test via Play License Testers."
                )
            }
            return
        }

        // India Alternative Billing via Razorpay — Step 3: Create Order strictly on secure backend
        if (activity == null) {
            failPayment("Unable to Launch Razorpay", "Activity context unavailable.")
            return
        }
        val activePaymentManager = customPaymentManager ?: paymentManager
        viewModelScope.launch {
            _paymentFlowState.value = state.copy(
                currentStep = PaymentScreenStep.PAYMENT_PROCESSING,
                processingStage = "Creating secure ₹${plan.priceInr} order on PaperPilot payment server..."
            )
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            if (idToken.isBlank()) {
                failPayment("Authentication Required", "Could not retrieve verified Google auth token.")
                return@launch
            }
            val idempotencyKey = "${user.uid}_${plan.tierId}_${UUID.randomUUID()}"
            val orderResult = paymentBackendClient.createOrder(
                firebaseIdToken = idToken,
                plan = plan,
                provider = PaymentGatewayProvider.RAZORPAY,
                idempotencyKey = idempotencyKey
            )
            orderResult.onSuccess { order ->
                _paymentFlowState.value = _paymentFlowState.value.copy(
                    activeOrder = order,
                    processingStage = "Opening official Razorpay Checkout for ₹${order.amountInr}..."
                )
                activePaymentManager.startPayment(
                    activity = activity,
                    order = order,
                    plan = plan,
                    method = method,
                    customerEmail = user.email.orEmpty(),
                    customerName = user.displayName.orEmpty()
                ).onFailure { err ->
                    failPayment(
                        title = "Razorpay Checkout Unavailable",
                        reason = err.message ?: "Unable to launch Razorpay Checkout SDK."
                    )
                }
            }.onFailure { err ->
                failPayment(
                    title = "Order Creation Failed",
                    reason = err.message ?: "Unable to connect to the secure payment backend."
                )
            }
        }
    }

    private fun handleRazorpayCheckoutEvent(event: RazorpayCheckoutEvent) {
        val user = firebaseAuth?.currentUser
        when (event) {
            is RazorpayCheckoutEvent.PaymentCapturedPendingVerification -> {
                if (user == null) {
                    failPayment("Sign-In Required", "Sign in with Google Mail to verify your payment.")
                    return
                }
                viewModelScope.launch {
                    _paymentFlowState.value = _paymentFlowState.value.copy(
                        currentStep = PaymentScreenStep.PAYMENT_PROCESSING,
                        processingStage = "Verifying Razorpay payment signature server-side..."
                    )
                    val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
                    if (idToken.isBlank()) {
                        failPayment("Authentication Required", "Unable to authenticate verification request.")
                        return@launch
                    }
                    val verifyResult = paymentBackendClient.verifyRazorpayPayment(
                        firebaseIdToken = idToken,
                        orderId = event.internalOrderId,
                        razorpayOrderId = event.razorpayOrderId,
                        razorpayPaymentId = event.razorpayPaymentId,
                        razorpaySignature = event.razorpaySignature,
                        paymentMethodLabel = event.paymentMethodLabel,
                        razorpaySubscriptionId = event.razorpaySubscriptionId
                    )
                    verifyResult.onSuccess { outcome ->
                        if (outcome.verified) {
                            AdMobManager.setPremiumActive(true, getApplication())
                            _paymentFlowState.value = _paymentFlowState.value.copy(
                                currentStep = PaymentScreenStep.PAYMENT_SUCCESSFUL,
                                lastSuccessTransaction = outcome.transaction
                            )
                            refreshMembershipStatusFromServer()
                            emitMessage("Premium Membership verified & activated! All ads disabled.")
                        } else {
                            failPayment(
                                title = "Payment Verification Failed",
                                reason = outcome.errorMessage ?: "Server rejected payment signature. Membership was not activated."
                            )
                        }
                    }.onFailure { err ->
                        failPayment(
                            title = "Server Verification Failed",
                            reason = err.message ?: "Unable to verify payment signature on backend."
                        )
                    }
                }
            }

            is RazorpayCheckoutEvent.PaymentCancelled -> {
                if (user != null && event.internalOrderId.isNotBlank() && paymentBackendClient.isConfigured()) {
                    viewModelScope.launch {
                        val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
                        if (idToken.isNotBlank()) {
                            paymentBackendClient.cancelOrder(idToken, event.internalOrderId, event.message)
                        }
                    }
                }
                failPayment("Payment Cancelled", event.message)
            }

            is RazorpayCheckoutEvent.PaymentFailed -> {
                failPayment("Razorpay Payment Failed", event.message)
            }
        }
    }

    fun refreshMembershipStatusFromServer() {
        val user = firebaseAuth?.currentUser ?: return
        if (!paymentBackendClient.isConfigured()) return
        viewModelScope.launch {
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            if (idToken.isBlank()) return@launch
            paymentBackendClient.getMembershipStatus(idToken).onSuccess { status ->
                AdMobManager.setPremiumActive(status.isPremiumActive, getApplication())
                _membershipState.value = _membershipState.value.copy(
                    isPremiumActive = status.isPremiumActive,
                    activePlan = MembershipPlan.fromTierId(status.planTier),
                    membershipStatus = status.effectiveStatus,
                    premiumUntilEpochMs = status.expiryDateMs,
                    autoPayEnabled = status.autoPayEnabled,
                    autoRenew = status.autoPayEnabled || _membershipState.value.autoRenew,
                    autoPayTargetPlan = status.autoPayTargetPlan,
                    autoPayMandateId = status.autoPayMandateId,
                    nextBillingAmountInr = status.nextBillingAmountInr,
                    isTrial = status.isTrial && status.isPremiumActive,
                    hasUsedTrial = status.hasUsedTrial
                )
            }
            paymentBackendClient.getPaymentHistory(idToken).onSuccess { history ->
                if (history.isNotEmpty()) {
                    _membershipState.value = _membershipState.value.copy(transactions = history)
                }
            }
        }
    }

    private fun handlePlayBillingEvent(event: PlayBillingEvent) {
        when (event) {
            is PlayBillingEvent.PurchaseReadyForServerVerification -> {
                val user = firebaseAuth?.currentUser
                if (user == null) {
                    failPayment("Sign-In Required", "Sign in with Google Mail to verify your Play purchase.")
                    return
                }
                viewModelScope.launch {
                    _paymentFlowState.value = _paymentFlowState.value.copy(
                        currentStep = PaymentScreenStep.PAYMENT_PROCESSING,
                        processingStage = "Verifying Google Play purchase token server-side..."
                    )
                    val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
                    val verifyResult = paymentBackendClient.verifyGooglePlayPurchase(
                        firebaseIdToken = idToken,
                        productId = event.productId,
                        purchaseToken = event.purchaseToken,
                        orderId = event.orderId
                    )
                    verifyResult.onSuccess { outcome ->
                        if (outcome.verified) {
                            if (!event.isAcknowledged) {
                                playBillingManager.acknowledgePurchase(event.purchaseToken)
                            }
                            AdMobManager.setPremiumActive(true, getApplication())
                            _paymentFlowState.value = _paymentFlowState.value.copy(
                                currentStep = PaymentScreenStep.PAYMENT_SUCCESSFUL,
                                lastSuccessTransaction = outcome.transaction
                            )
                            emitMessage("Premium Membership verified & activated! All ads disabled.")
                        } else {
                            failPayment("Verification Failed", outcome.errorMessage ?: "Server rejected Play purchase token.")
                        }
                    }.onFailure { err ->
                        failPayment("Server Verification Failed", err.message ?: "Unable to verify Play purchase on server.")
                    }
                }
            }
            is PlayBillingEvent.PurchaseCancelled -> {
                failPayment("Payment Cancelled", event.message)
            }
            is PlayBillingEvent.PurchaseFailed -> {
                failPayment("Google Play Billing Error", event.message)
            }
        }
    }

    private fun failPayment(title: String, reason: String) {
        _paymentFlowState.value = _paymentFlowState.value.copy(
            currentStep = PaymentScreenStep.PAYMENT_FAILED,
            lastFailureTitle = title,
            lastFailureReason = reason
        )
    }

    fun restorePurchases() {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            emitMessage("Sign in with Google Mail to restore your purchases.")
            return
        }
        viewModelScope.launch {
            _paymentFlowState.value = _paymentFlowState.value.copy(isRestoringPurchases = true)
            // 1. Check active Google Play subscriptions and verify any unverified tokens server-side
            val activePlayPurchases = playBillingManager.queryActiveSubscriptions()
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            if (activePlayPurchases.isNotEmpty() && idToken.isNotBlank() && paymentBackendClient.isConfigured()) {
                activePlayPurchases.forEach { purchase ->
                    val sku = purchase.products.firstOrNull().orEmpty()
                    val verified = paymentBackendClient.verifyGooglePlayPurchase(
                        firebaseIdToken = idToken,
                        productId = sku,
                        purchaseToken = purchase.purchaseToken,
                        orderId = purchase.orderId.orEmpty()
                    )
                    if (verified.getOrNull()?.verified == true && !purchase.isAcknowledged) {
                        playBillingManager.acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
            // 2. Ask server to reconcile expiry & entitlements when backend is configured
            if (idToken.isNotBlank() && paymentBackendClient.isConfigured()) {
                paymentBackendClient.restoreSubscription(idToken)
                    .onSuccess { active ->
                        AdMobManager.setPremiumActive(active, getApplication())
                        emitMessage(if (active) "Premium Membership restored!" else "No active Premium subscription found.")
                    }
                    .onFailure { err ->
                        emitMessage(err.message ?: "Could not restore purchases from server.")
                    }
            } else {
                // Re-sync live Firestore entitlement directly
                val active = _membershipState.value.isPremiumActive &&
                    _membershipState.value.premiumUntilEpochMs > System.currentTimeMillis()
                AdMobManager.setPremiumActive(active, getApplication())
                emitMessage(
                    if (active) "Active Premium status verified from Firestore."
                    else "No active Play or Firestore subscription found for ${user.email}."
                )
            }
            _paymentFlowState.value = _paymentFlowState.value.copy(isRestoringPurchases = false)
        }
    }

    fun cancelPremiumSubscription(reason: String) {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            emitMessage("Sign in with Google Mail to manage your subscription.")
            return
        }
        viewModelScope.launch {
            _paymentFlowState.value = _paymentFlowState.value.copy(isCancellingSubscription = true)
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            paymentBackendClient.cancelSubscription(
                firebaseIdToken = idToken,
                reason = reason,
                immediate = false
            ).onSuccess {
                emitMessage("Subscription cancellation recorded. Access continues until expiry.")
                _paymentFlowState.value = _paymentFlowState.value.copy(
                    isCancellingSubscription = false,
                    currentStep = PaymentScreenStep.MEMBERSHIP_DETAILS
                )
            }.onFailure { err ->
                emitMessage(err.message ?: "Connect payment backend or cancel via Google Play Subscriptions.")
                _paymentFlowState.value = _paymentFlowState.value.copy(isCancellingSubscription = false)
            }
        }
    }

    fun refreshAdminDashboard() {
        if (!_membershipState.value.isAdmin) return
        val repo = membershipRepository ?: return
        viewModelScope.launch {
            _membershipState.value = _membershipState.value.copy(isLoadingAdmin = true, adminError = null)
            repo.loadAdminDashboardSnapshot()
                .onSuccess { snapshot ->
                    _membershipState.value = _membershipState.value.copy(
                        adminMetrics = snapshot,
                        isLoadingAdmin = false,
                        adminError = null
                    )
                }
                .onFailure { err ->
                    _membershipState.value = _membershipState.value.copy(
                        isLoadingAdmin = false,
                        adminError = err.localizedMessage ?: "Failed to load admin metrics."
                    )
                }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun navigateTo(destination: AppDestination) {
        _backStack.value = _backStack.value + destination
    }

    fun navigateBack(): Boolean {
        val current = _backStack.value
        return if (current.size > 1) {
            _backStack.value = current.dropLast(1)
            true
        } else {
            false
        }
    }

    fun popToMainTabs(tab: MainTab? = null) {
        if (tab != null) _selectedTab.value = tab
        _backStack.value = listOf(AppDestination.MainTabs)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingComplete(true)
            popToMainTabs(MainTab.HOME)
        }
    }

    fun openDocument(doc: DocumentEntity, bypassPassword: Boolean = false) {
        if (doc.isProtected && !bypassPassword) {
            _passwordPromptDoc.value = doc
            return
        }
        viewModelScope.launch {
            repository.touchOpened(doc.id)
        }
        navigateTo(AppDestination.PdfViewer(doc.id))
    }

    fun dismissPasswordPrompt() {
        _passwordPromptDoc.value = null
    }

    fun verifyAndOpenProtected(doc: DocumentEntity, password: String): Boolean {
        val ok = doc.passwordHash == null || DocumentRepository.hashPassword(password) == doc.passwordHash
        if (ok) {
            _passwordPromptDoc.value = null
            openDocument(doc, bypassPassword = true)
        }
        return ok
    }

    fun openEditor(doc: DocumentEntity, initialTool: EditorTool = EditorTool.EDIT_CONTENT) {
        navigateTo(AppDestination.PdfEditor(doc.id, initialTool))
    }

    fun openTool(toolId: ToolId, preselectedDocIds: List<Long> = emptyList()) {
        navigateTo(AppDestination.ToolFlow(toolId, preselectedDocIds))
    }

    fun importUris(uris: List<Uri>, source: DocumentSource = DocumentSource.OPENED, onImported: (List<DocumentEntity>) -> Unit = {}) {
        viewModelScope.launch {
            val imported = uris.mapNotNull { repository.importFromUri(it, source) }
            if (imported.isNotEmpty()) {
                emitMessage(
                    if (imported.size == 1) "Imported ${imported.first().name}"
                    else "Imported ${imported.size} files"
                )
            }
            onImported(imported)
        }
    }

    fun createFromTemplate(template: DocumentTemplate) {
        viewModelScope.launch {
            val (file, text) = SamplePdfGenerator.generateFromTemplate(template, repository.documentsDir)
            val doc = repository.registerOutputFile(
                file = file,
                displayName = template.defaultFileName,
                mimeType = "application/pdf",
                source = DocumentSource.CREATED,
                extractedText = text
            )
            emitMessage("Created ${doc.name}")
            popToMainTabs(MainTab.HOME)
            openEditor(doc, EditorTool.EDIT_CONTENT)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, !doc.isFavorite)
            emitMessage(if (!doc.isFavorite) "Added to Favorites" else "Removed from Favorites")
        }
    }

    fun renameDocument(doc: DocumentEntity, newName: String) {
        viewModelScope.launch {
            repository.renameDocument(doc.id, newName)
            emitMessage("Renamed to $newName")
        }
    }

    fun deleteDocumentWithUndo(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.moveToBin(doc)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Moved ${doc.name} to Bin",
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch { repository.restoreFromBin(doc) }
                    }
                )
            )
        }
    }

    fun deleteDocumentsWithUndo(docs: List<DocumentEntity>) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            repository.moveToBinMultiple(docs)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Moved ${docs.size} files to Bin",
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch { repository.restoreFromBinMultiple(docs) }
                    }
                )
            )
        }
    }

    fun restoreFromBin(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.restoreFromBin(doc)
            emitMessage("Restored ${doc.name}")
        }
    }

    fun permanentlyDeleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteDocument(doc)
            emitMessage("Permanently deleted ${doc.name}")
        }
    }

    fun emptyBin() {
        val currentTrashed = trashedDocuments.value
        if (currentTrashed.isEmpty()) return
        viewModelScope.launch {
            repository.emptyBin(currentTrashed)
            emitMessage("Bin emptied")
        }
    }

    fun runTool(
        tool: ToolId,
        selectedFiles: List<DocumentEntity>,
        options: ToolOptions,
        onProgress: (Float, String) -> Unit,
        onSuccess: (ToolOutputResult, DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        activeToolJob?.cancel()
        activeToolJob = viewModelScope.launch {
            try {
                val result = ToolEngine.run(
                    tool = tool,
                    files = selectedFiles,
                    options = options,
                    outputDir = repository.documentsDir,
                    onProgress = onProgress
                )
                val savedDoc = repository.registerOutputFile(
                    file = result.outputFile,
                    displayName = result.displayName,
                    mimeType = result.mimeType,
                    source = DocumentSource.CREATED,
                    isProtected = result.isProtected,
                    passwordHash = result.passwordHash,
                    allowPrint = result.allowPrint,
                    allowCopy = result.allowCopy,
                    allowEdit = result.allowEdit,
                    extractedText = result.extractedText ?: ""
                )
                settingsStore.recordToolUsage(toolKey = tool.name)
                onSuccess(result, savedDoc)
            } catch (_: CancellationException) {
                onError("Cancelled")
            } catch (e: Exception) {
                onError(e.message ?: "Processing failed. Please try again.")
            }
        }
    }

    fun cancelActiveTool() {
        activeToolJob?.cancel()
    }

    fun saveEditedPdf(
        originalDoc: DocumentEntity,
        annotations: List<PageAnnotation>,
        editedContentBlocks: List<EditableTextBlock> = emptyList(),
        overwriteOriginal: Boolean,
        onSaved: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val baseName = originalDoc.name.substringBeforeLast(".")
                val outFile = if (overwriteOriginal) {
                    File(originalDoc.filePath)
                } else {
                    File(repository.documentsDir, "${baseName}_edited_${System.currentTimeMillis()}.pdf")
                }
                val tempTarget = File(repository.documentsDir, "tmp_flat_${System.currentTimeMillis()}.pdf")
                PdfFlattener.flattenAnnotationsToPdf(
                    sourcePdf = File(originalDoc.filePath),
                    outputPdf = tempTarget,
                    annotations = annotations,
                    editedContentBlocks = editedContentBlocks
                )
                tempTarget.copyTo(outFile, overwrite = true)
                tempTarget.delete()

                val finalDoc = if (overwriteOriginal) {
                    val thumb = PdfRenderHelper.generateThumbnail(outFile, repository.thumbnailsDir)
                    val updated = originalDoc.copy(
                        sizeBytes = outFile.length(),
                        thumbnailPath = thumb ?: originalDoc.thumbnailPath,
                        lastOpenedAt = System.currentTimeMillis()
                    )
                    repository.updateDocument(updated)
                    updated
                } else {
                    repository.registerOutputFile(
                        file = outFile,
                        displayName = "${baseName}_edited.pdf",
                        mimeType = "application/pdf",
                        source = DocumentSource.CREATED,
                        extractedText = originalDoc.extractedText
                    )
                }
                emitMessage(if (overwriteOriginal) "Updated ${finalDoc.name}" else "Saved copy: ${finalDoc.name}")
                onSaved(finalDoc)
            } catch (e: Exception) {
                emitMessage("Save failed: ${e.message}")
            }
        }
    }

    fun saveTypedSignature(text: String, colorArgb: Int, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_typed_${System.currentTimeMillis()}.png")
            PdfFlattener.renderTypedSignatureBitmap(text, colorArgb, outFile)
            val entity = repository.saveSignature(text.ifBlank { "Typed Signature" }, "TYPED", outFile)
            onSaved(entity)
        }
    }

    fun saveDrawnSignature(strokes: List<List<Pair<Float, Float>>>, colorArgb: Int, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_drawn_${System.currentTimeMillis()}.png")
            PdfFlattener.renderDrawnSignatureBitmap(strokes, colorArgb, outFile)
            val entity = repository.saveSignature("Handwritten Signature", "DRAWN", outFile)
            onSaved(entity)
        }
    }

    fun saveImageSignature(uri: Uri, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_img_${System.currentTimeMillis()}.png")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { input.copyTo(it) }
            }
            if (outFile.exists()) {
                val entity = repository.saveSignature("Uploaded Signature", "IMAGE", outFile)
                onSaved(entity)
            }
        }
    }

    fun deleteSignature(id: Long) {
        viewModelScope.launch { repository.deleteSignature(id) }
    }

    fun saveScannedBatchToPdf(
        pageFiles: List<File>,
        filterNames: List<String>,
        enableOcr: Boolean,
        ocrLang: String,
        pdfTitle: String = "",
        pageSize: com.example.engine.PageSizeOption = com.example.engine.PageSizeOption.A4,
        margin: com.example.engine.MarginOption = com.example.engine.MarginOption.NONE,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                if (pageFiles.isEmpty()) {
                    onError("Capture or import at least one page first.")
                    return@launch
                }
                val stamp = System.currentTimeMillis()
                pageFiles.forEachIndexed { idx, file ->
                    repository.addScannedImage(
                        name = "Scan_${idx + 1}.jpg",
                        imageFile = file,
                        filterName = filterNames.getOrElse(idx) { "Auto-enhance" }
                    )
                }
                val tempDocs = pageFiles.mapIndexed { idx, f ->
                    DocumentEntity(
                        name = "Page_${idx + 1}.jpg",
                        filePath = f.absolutePath,
                        mimeType = "image/jpeg",
                        sizeBytes = f.length()
                    )
                }
                val result = ToolEngine.run(
                    tool = ToolId.IMAGE_TO_PDF,
                    files = tempDocs,
                    options = ToolOptions(
                        pageSize = pageSize,
                        margin = margin
                    ),
                    outputDir = repository.documentsDir,
                    onProgress = { _, _ -> }
                )
                val cleanName = pdfTitle.trim()
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .removeSuffix(".pdf")
                    .ifBlank { "Scan_${SimpleDateFormat("MMMdd_HHmm", Locale.US).format(Date(stamp))}" }
                val finalDisplayName = "$cleanName.pdf"
                val ocrNote = if (enableOcr) {
                    "[Scanned with paperpilot OCR • $ocrLang • ${pageFiles.size} page(s)]"
                } else ""
                val saved = repository.registerOutputFile(
                    file = result.outputFile,
                    displayName = finalDisplayName,
                    mimeType = "application/pdf",
                    source = DocumentSource.SCANNED,
                    extractedText = ocrNote
                )
                emitMessage("Created $finalDisplayName (${pageFiles.size} page${if (pageFiles.size == 1) "" else "s"})")
                onCreated(saved)
            } catch (e: Exception) {
                val msg = "Failed to create PDF: ${e.message ?: "unknown error"}"
                emitMessage(msg)
                onError(msg)
            }
        }
    }

    fun shareDocument(doc: DocumentEntity): Intent? = repository.createShareIntent(doc)

    fun printDocument(activityContext: android.content.Context, doc: DocumentEntity) {
        val file = File(doc.filePath)
        if (!file.exists()) {
            emitMessage("File unavailable to print")
            return
        }
        val printManager = activityContext.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            emitMessage("Printing is unavailable on this device")
            return
        }
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(doc.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(doc.pageCount)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    file.inputStream().use { input ->
                        java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(doc.name, adapter, PrintAttributes.Builder().build())
    }

    // Settings mutators
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch { settingsStore.setAccentPreset(preset) }
    fun setDefaultSaveFolder(f: String) = viewModelScope.launch { settingsStore.setDefaultSaveFolder(f) }
    fun setOcrLanguage(l: String) = viewModelScope.launch { settingsStore.setOcrLanguage(l) }
    fun setDefaultImageQuality(q: Int) = viewModelScope.launch { settingsStore.setDefaultImageQuality(q) }
    fun setKeepOriginalFiles(k: Boolean) = viewModelScope.launch { settingsStore.setKeepOriginalFiles(k) }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(SnackbarEvent(msg))
    }

    fun getQuickTools(): List<ToolId> {
        val counts = settings.value.usageMap()
        if (counts.isEmpty()) return ToolId.defaultQuickTools
        val sortedUsed = counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry -> ToolId.entries.firstOrNull { it.name == entry.key } }
        return (sortedUsed + ToolId.defaultQuickTools).distinct().take(6)
    }
}
