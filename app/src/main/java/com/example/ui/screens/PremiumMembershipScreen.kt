package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.ads.AdMobManager
import com.example.ads.findActivity
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutGoogle
import com.example.payment.PaymentBackendClient
import com.example.payment.PaymentFlowUiState
import com.example.payment.PaymentGatewayProvider
import com.example.payment.PaymentManager
import com.example.payment.PaymentMethodOption
import com.example.payment.PaymentScreenStep
import com.example.payment.ReceiptPdfGenerator
import com.example.ui.components.LogoVariant
import com.example.ui.components.PaperPilotLogo
import com.example.ui.screens.payment.AdminDashboardStep
import com.example.ui.screens.payment.CancelSubscriptionStep
import com.example.ui.screens.payment.MembershipDetailsStep
import com.example.ui.screens.payment.OrderSummaryStep
import com.example.ui.screens.payment.PaperPilotBlueHeroCard
import com.example.ui.screens.payment.PaperPilotPaymentColors
import com.example.ui.screens.payment.PaymentFailedStep
import com.example.ui.screens.payment.PaymentMethodStep
import com.example.ui.screens.payment.PaymentProcessingStep
import com.example.ui.screens.payment.PaymentSuccessfulStep
import com.example.ui.screens.payment.PremiumAccountShortcutsCard
import com.example.ui.screens.payment.PremiumGoogleAccountCard
import com.example.ui.screens.payment.PremiumRewardedAdBonusCard
import com.example.ui.screens.payment.SecurePaymentBadge
import com.example.ui.screens.payment.SelectPlanStep
import com.example.ui.screens.payment.TransactionHistoryStep
import com.example.ui.screens.payment.formatPaymentDate
import com.example.viewmodel.MembershipUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Blue & White PaperPilot Premium Membership Screen.
 *
 * Displays the **₹99/month (Monthly Premium)** and **₹999/year (Yearly Premium)** plan cards
 * and integrates directly with [PaymentManager] to initialize Razorpay Checkout and initiate
 * server-verified payment transactions (plus hosting all 10 payment lifecycle screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumMembershipScreen(
    membershipState: MembershipUiState,
    paymentFlowState: PaymentFlowUiState,
    paymentManager: PaymentManager? = null,
    onBack: () -> Unit,
    onGoogleAuthSucceeded: () -> Unit,
    onBeforeSignOut: () -> Unit,
    onSignOutCompleted: () -> Unit,
    onSetStep: (PaymentScreenStep) -> Unit,
    onSelectPlan: (MembershipPlan) -> Unit,
    onSelectAutoPayTargetPlan: (MembershipPlan) -> Unit = {},
    onSelectMethod: (PaymentMethodOption) -> Unit,
    onStartCheckout: () -> Unit,
    onTriggerRazorpayOrder: ((MembershipPlan, PaymentManager) -> Unit)? = null,
    onStartTrialAutoPay: ((MembershipPlan, PaymentManager) -> Unit)? = null,
    onToggleAutoPay: (Boolean) -> Unit = {},
    onRestorePurchases: () -> Unit,
    onCancelSubscription: (String) -> Unit,
    onRefreshAdmin: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val resolvedPaymentManager = remember(context, paymentManager) {
        paymentManager ?: PaymentManager(context.applicationContext)
    }
    val backendClient = remember { PaymentBackendClient() }

    // Preload Razorpay Checkout SDK as soon as PremiumMembershipScreen enters composition
    LaunchedEffect(resolvedPaymentManager) {
        resolvedPaymentManager.initialize()
    }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    fun handleBackStep() {
        when (paymentFlowState.currentStep) {
            PaymentScreenStep.PREMIUM_MEMBERSHIP -> onBack()
            PaymentScreenStep.SELECT_PLAN -> onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP)
            PaymentScreenStep.PAYMENT_METHOD -> onSetStep(PaymentScreenStep.SELECT_PLAN)
            PaymentScreenStep.ORDER_SUMMARY -> onSetStep(PaymentScreenStep.PAYMENT_METHOD)
            PaymentScreenStep.PAYMENT_PROCESSING -> Unit
            PaymentScreenStep.PAYMENT_SUCCESSFUL -> onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP)
            PaymentScreenStep.PAYMENT_FAILED -> onSetStep(PaymentScreenStep.ORDER_SUMMARY)
            PaymentScreenStep.TRANSACTION_HISTORY -> onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP)
            PaymentScreenStep.MEMBERSHIP_DETAILS -> onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP)
            PaymentScreenStep.CANCEL_SUBSCRIPTION -> onSetStep(PaymentScreenStep.MEMBERSHIP_DETAILS)
            PaymentScreenStep.ADMIN_DASHBOARD -> onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP)
        }
    }

    BackHandler(onBack = ::handleBackStep)

    fun launchGoogleSignIn() {
        isSigningIn = true
        authError = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {
                isSigningIn = false
                onGoogleAuthSucceeded()
            },
            onAuthError = { msg ->
                isSigningIn = false
                authError = msg
                onNotify(msg)
            },
            onAuthCancelled = {
                isSigningIn = false
            }
        )
    }

    /**
     * Initiates a transaction for the chosen ₹99/month or ₹999/year plan via [PaymentManager].
     * Always creates an authoritative order on the backend before opening Razorpay Checkout.
     */
    fun initiatePlanTransaction(plan: MembershipPlan) {
        onSelectPlan(plan)
        if (!membershipState.isSignedIn) {
            launchGoogleSignIn()
            return
        }
        if (onTriggerRazorpayOrder != null) {
            onTriggerRazorpayOrder(plan, resolvedPaymentManager)
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onNotify("Activity unavailable to open Razorpay Checkout.")
            return
        }
        scope.launch {
            val user = runCatching { Firebase.auth.currentUser }.getOrNull()
            val idToken = runCatching { user?.getIdToken(false)?.await()?.token.orEmpty() }.getOrDefault("")
            if (user == null || idToken.isBlank()) {
                onNotify("Please sign in with Google Mail to initiate payment.")
                return@launch
            }
            backendClient.createOrder(
                firebaseIdToken = idToken,
                plan = plan,
                provider = PaymentGatewayProvider.RAZORPAY,
                idempotencyKey = "${user.uid}_${plan.tierId}_${UUID.randomUUID()}"
            ).onSuccess { order ->
                resolvedPaymentManager.startPayment(
                    activity = activity,
                    order = order,
                    plan = plan,
                    method = PaymentMethodOption.RAZORPAY_UPI,
                    customerEmail = user.email.orEmpty(),
                    customerName = user.displayName.orEmpty()
                ).onFailure { err ->
                    onNotify(err.message ?: "Unable to launch Razorpay Checkout.")
                }
            }.onFailure { err ->
                onNotify(err.message ?: "Unable to create backend payment order.")
            }
        }
    }

    /**
     * Initiates the ₹1 for 1-Day Trial + UPI AutoPay setup via [PaymentManager].
     */
    fun initiateTrialAutoPay(targetPlan: MembershipPlan) {
        onSelectAutoPayTargetPlan(targetPlan)
        if (!membershipState.isSignedIn) {
            launchGoogleSignIn()
            return
        }
        if (onStartTrialAutoPay != null) {
            onStartTrialAutoPay(targetPlan, resolvedPaymentManager)
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onNotify("Activity unavailable to open Razorpay Checkout.")
            return
        }
        scope.launch {
            val user = runCatching { Firebase.auth.currentUser }.getOrNull()
            val idToken = runCatching { user?.getIdToken(false)?.await()?.token.orEmpty() }.getOrDefault("")
            if (user == null || idToken.isBlank()) {
                onNotify("Please sign in with Google Mail to start your ₹1 trial.")
                return@launch
            }
            backendClient.createTrialAutoPayOrder(
                firebaseIdToken = idToken,
                autoPayTargetPlan = targetPlan,
                idempotencyKey = "${user.uid}_TRIAL_1DAY_${targetPlan.tierId}_${UUID.randomUUID()}"
            ).onSuccess { order ->
                resolvedPaymentManager.startPayment(
                    activity = activity,
                    order = order,
                    plan = MembershipPlan.TRIAL_1DAY,
                    method = PaymentMethodOption.RAZORPAY_UPI,
                    customerEmail = user.email.orEmpty(),
                    customerName = user.displayName.orEmpty()
                ).onFailure { err ->
                    onNotify(err.message ?: "Unable to launch Razorpay AutoPay Checkout.")
                }
            }.onFailure { err ->
                onNotify(err.message ?: "Unable to create ₹1 trial AutoPay order.")
            }
        }
    }

    fun downloadAndShareReceipt(tx: PaymentTransaction) {
        ReceiptPdfGenerator.generateReceiptPdf(
            context = context,
            transaction = tx,
            customerEmail = membershipState.userEmail,
            customerName = membershipState.displayName
        ).onSuccess { file ->
            val intent = ReceiptPdfGenerator.buildShareReceiptIntent(context, file)
            context.startActivity(Intent.createChooser(intent, "Save or Share PaperPilot Receipt"))
            onNotify("Receipt PDF saved: ${file.name}")
        }.onFailure { err ->
            onNotify("Could not generate receipt: ${err.localizedMessage ?: "error"}")
        }
    }

    val topTitle = when (paymentFlowState.currentStep) {
        PaymentScreenStep.PREMIUM_MEMBERSHIP -> "PaperPilot Premium"
        PaymentScreenStep.SELECT_PLAN -> "Select Plan"
        PaymentScreenStep.PAYMENT_METHOD -> "Payment Method"
        PaymentScreenStep.ORDER_SUMMARY -> "Order Summary"
        PaymentScreenStep.PAYMENT_PROCESSING -> "Processing Payment"
        PaymentScreenStep.PAYMENT_SUCCESSFUL -> "Payment Successful"
        PaymentScreenStep.PAYMENT_FAILED -> "Payment Failed"
        PaymentScreenStep.TRANSACTION_HISTORY -> "Transaction History"
        PaymentScreenStep.MEMBERSHIP_DETAILS -> "Membership Details"
        PaymentScreenStep.CANCEL_SUBSCRIPTION -> "Cancel Subscription"
        PaymentScreenStep.ADMIN_DASHBOARD -> "Admin Payment Console"
    }

    Scaffold(
        containerColor = Color(0xFFF5F8FF),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PaperPilotLogo(size = 24.dp, variant = LogoVariant.BADGE)
                            }
                        }
                        Text(topTitle, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PaperPilotPaymentColors.BlueDeep,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    if (paymentFlowState.currentStep != PaymentScreenStep.PAYMENT_PROCESSING) {
                        IconButton(
                            onClick = ::handleBackStep,
                            modifier = Modifier.testTag("premium_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                when (paymentFlowState.currentStep) {
                    PaymentScreenStep.PREMIUM_MEMBERSHIP -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PaperPilotBlueHeroCard(
                                title = if (membershipState.isPremiumActive) "Premium Active • Zero Ads" else "Upgrade to PaperPilot Premium",
                                subtitle = if (membershipState.isPremiumActive) {
                                    "Your ${membershipState.activePlan?.title ?: "Premium"} plan is active until ${formatPaymentDate(membershipState.premiumUntilEpochMs)}. All AdMob ads are disabled."
                                } else {
                                    "Go ad-free and unlock advanced PDF editing, conversions, and priority support."
                                },
                                badgeText = if (membershipState.isPremiumActive) {
                                    if (membershipState.isTrial) "₹1 TRIAL ACTIVE" else "VERIFIED PRO"
                                } else {
                                    "₹1 TRIAL • ₹99/MO • ₹999/YR"
                                }
                            )
                        }

                        item {
                            PremiumGoogleAccountCard(
                                membershipState = membershipState,
                                isSigningIn = isSigningIn,
                                authError = authError,
                                onSignIn = ::launchGoogleSignIn,
                                onSignOut = {
                                    signOutGoogle(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onBeforeFirebaseSignOut = onBeforeSignOut,
                                        onSignOutComplete = onSignOutCompleted
                                    )
                                }
                            )
                        }

                        // Section header for ₹99/month & ₹999/year cards
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "Membership Plans",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PaperPilotPaymentColors.BlueDeep
                                    )
                                    Text(
                                        text = "UPI, Google Pay, PhonePe, Cards, Net Banking & Play Billing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PaperPilotPaymentColors.TextMuted
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = PaperPilotPaymentColors.IceSurface,
                                    border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary, modifier = Modifier.size(13.dp))
                                        Text("Razorpay", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PaperPilotPaymentColors.BlueDeep)
                                    }
                                }
                            }
                        }

                        // ₹1 for 1-Day Trial + UPI AutoPay Offer Card (one-time per Google account)
                        if (!membershipState.hasUsedTrial || membershipState.isTrial) {
                            item {
                                TrialAutoPayOfferCard(
                                    selectedAutoPayPlan = paymentFlowState.autoPayTargetPlan,
                                    isTrialCurrentlyActive = membershipState.isTrial && membershipState.isPremiumActive,
                                    autoPayEnabled = membershipState.autoPayEnabled,
                                    trialExpiryMs = membershipState.premiumUntilEpochMs,
                                    onSelectAutoPayPlan = onSelectAutoPayTargetPlan,
                                    onStartTrialAutoPay = {
                                        initiateTrialAutoPay(paymentFlowState.autoPayTargetPlan)
                                    },
                                    onManageAutoPay = {
                                        onSetStep(PaymentScreenStep.MEMBERSHIP_DETAILS)
                                    }
                                )
                            }
                        }

                        // Monthly Plan Card — ₹99/month (Crisp White & Cobalt Blue Material 3 Card)
                        item {
                            PremiumPlanPricingCard(
                                plan = MembershipPlan.MONTHLY,
                                priceLabel = paymentFlowState.playStorePrices[MembershipPlan.MONTHLY] ?: "₹99",
                                subtitleLine = "Flexible 30-day cycle • Cancel anytime before renewal",
                                badgeLabel = if (membershipState.activePlan == MembershipPlan.MONTHLY) "ACTIVE PLAN" else "MONTHLY • ₹99",
                                isHighlightedBlue = false,
                                isSelected = paymentFlowState.selectedPlan == MembershipPlan.MONTHLY,
                                cardTestTag = "plan_card_monthly_99",
                                ctaTestTag = "subscribe_monthly_button",
                                onSelectCard = { onSelectPlan(MembershipPlan.MONTHLY) },
                                onSubscribeNow = { initiatePlanTransaction(MembershipPlan.MONTHLY) },
                                onAllMethods = {
                                    onSelectPlan(MembershipPlan.MONTHLY)
                                    onSetStep(PaymentScreenStep.PAYMENT_METHOD)
                                }
                            )
                        }

                        // Featured Yearly Plan Card — ₹999/year (Royal Blue & White Material 3 Card)
                        item {
                            PremiumPlanPricingCard(
                                plan = MembershipPlan.YEARLY,
                                priceLabel = paymentFlowState.playStorePrices[MembershipPlan.YEARLY] ?: "₹999",
                                subtitleLine = "Just ₹83.25/month • Save 16% vs Monthly • Automatic yearly renewal where supported",
                                badgeLabel = if (membershipState.activePlan == MembershipPlan.YEARLY) "ACTIVE PLAN" else "BEST VALUE • SAVE 16%",
                                isHighlightedBlue = true,
                                isSelected = paymentFlowState.selectedPlan == MembershipPlan.YEARLY,
                                cardTestTag = "plan_card_yearly_999",
                                ctaTestTag = "subscribe_yearly_button",
                                onSelectCard = { onSelectPlan(MembershipPlan.YEARLY) },
                                onSubscribeNow = { initiatePlanTransaction(MembershipPlan.YEARLY) },
                                onAllMethods = {
                                    onSelectPlan(MembershipPlan.YEARLY)
                                    onSetStep(PaymentScreenStep.PAYMENT_METHOD)
                                }
                            )
                        }

                        item { SecurePaymentBadge() }

                        item {
                            PremiumAccountShortcutsCard(
                                membershipState = membershipState,
                                onOpenMembershipDetails = { onSetStep(PaymentScreenStep.MEMBERSHIP_DETAILS) },
                                onOpenTransactionHistory = { onSetStep(PaymentScreenStep.TRANSACTION_HISTORY) },
                                onRestorePurchases = onRestorePurchases,
                                onOpenAdminDashboard = { onSetStep(PaymentScreenStep.ADMIN_DASHBOARD) }
                            )
                        }

                        if (!membershipState.isPremiumActive) {
                            item {
                                PremiumRewardedAdBonusCard(
                                    onWatchRewardedAd = {
                                        AdMobManager.showRewardedAd(
                                            activity = context.findActivity(),
                                            onRewardEarned = { onNotify("Reward unlocked! Ads paused for 30 minutes.") },
                                            onUnavailable = { msg -> onNotify(msg) },
                                            onDismissed = { onNotify("Watch the full video to unlock 30 min ad-free.") }
                                        )
                                    }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    PaymentScreenStep.SELECT_PLAN -> SelectPlanStep(
                        selectedPlan = paymentFlowState.selectedPlan,
                        activePlan = membershipState.activePlan,
                        playStorePrices = paymentFlowState.playStorePrices,
                        onSelectPlan = onSelectPlan,
                        onContinue = { onSetStep(PaymentScreenStep.PAYMENT_METHOD) }
                    )

                    PaymentScreenStep.PAYMENT_METHOD -> PaymentMethodStep(
                        selectedPlan = paymentFlowState.selectedPlan,
                        selectedMethod = paymentFlowState.selectedMethod,
                        onSelectMethod = onSelectMethod,
                        onContinueToSummary = { onSetStep(PaymentScreenStep.ORDER_SUMMARY) }
                    )

                    PaymentScreenStep.ORDER_SUMMARY -> OrderSummaryStep(
                        flowState = paymentFlowState,
                        userEmail = membershipState.userEmail,
                        isSignedIn = membershipState.isSignedIn,
                        onSignInFirst = ::launchGoogleSignIn,
                        onConfirmAndPay = onStartCheckout,
                        onChangeMethod = { onSetStep(PaymentScreenStep.PAYMENT_METHOD) }
                    )

                    PaymentScreenStep.PAYMENT_PROCESSING -> PaymentProcessingStep(
                        stageText = paymentFlowState.processingStage,
                        plan = paymentFlowState.selectedPlan,
                        method = paymentFlowState.selectedMethod
                    )

                    PaymentScreenStep.PAYMENT_SUCCESSFUL -> PaymentSuccessfulStep(
                        transaction = paymentFlowState.lastSuccessTransaction ?: membershipState.transactions.firstOrNull(),
                        onDownloadReceipt = ::downloadAndShareReceipt,
                        onViewMembership = { onSetStep(PaymentScreenStep.MEMBERSHIP_DETAILS) },
                        onDone = onBack
                    )

                    PaymentScreenStep.PAYMENT_FAILED -> PaymentFailedStep(
                        title = paymentFlowState.lastFailureTitle,
                        reason = paymentFlowState.lastFailureReason,
                        onRetry = onStartCheckout,
                        onChangeMethod = { onSetStep(PaymentScreenStep.PAYMENT_METHOD) },
                        onBackToOverview = { onSetStep(PaymentScreenStep.PREMIUM_MEMBERSHIP) }
                    )

                    PaymentScreenStep.TRANSACTION_HISTORY -> TransactionHistoryStep(
                        transactions = membershipState.transactions,
                        refunds = membershipState.refunds,
                        onDownloadReceipt = ::downloadAndShareReceipt
                    )

                    PaymentScreenStep.MEMBERSHIP_DETAILS -> MembershipDetailsStep(
                        membershipState = membershipState,
                        latestTransaction = membershipState.transactions.firstOrNull { it.status == "SUCCESS" },
                        isRestoring = paymentFlowState.isRestoringPurchases,
                        isUpdatingAutoPay = paymentFlowState.isUpdatingAutoPay,
                        onToggleAutoPay = onToggleAutoPay,
                        onUpgradeOrRenew = { onSetStep(PaymentScreenStep.SELECT_PLAN) },
                        onRestorePurchases = onRestorePurchases,
                        onOpenHistory = { onSetStep(PaymentScreenStep.TRANSACTION_HISTORY) },
                        onOpenCancel = { onSetStep(PaymentScreenStep.CANCEL_SUBSCRIPTION) },
                        onDownloadReceipt = ::downloadAndShareReceipt
                    )

                    PaymentScreenStep.CANCEL_SUBSCRIPTION -> CancelSubscriptionStep(
                        membershipState = membershipState,
                        isCancelling = paymentFlowState.isCancellingSubscription,
                        onConfirmCancel = onCancelSubscription,
                        onOpenPlaySubscriptions = {
                            val playUri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, playUri))
                        },
                        onKeepPremium = { onSetStep(PaymentScreenStep.MEMBERSHIP_DETAILS) }
                    )

                    PaymentScreenStep.ADMIN_DASHBOARD -> AdminDashboardStep(
                        metrics = membershipState.adminMetrics,
                        isLoading = membershipState.isLoadingAdmin,
                        errorMessage = membershipState.adminError,
                        onRefresh = onRefreshAdmin
                    )
                }
            }
        }
    }
}

/**
 * Material 3 blue-and-white pricing card for PaperPilot's Monthly (₹99) and Yearly (₹999) plans.
 * Each card includes a 'Subscribe Now' CTA button wired directly to [PaymentManager].
 */
@Composable
fun PremiumPlanPricingCard(
    plan: MembershipPlan,
    priceLabel: String,
    subtitleLine: String,
    badgeLabel: String,
    isHighlightedBlue: Boolean,
    isSelected: Boolean,
    cardTestTag: String,
    ctaTestTag: String,
    onSelectCard: () -> Unit,
    onSubscribeNow: () -> Unit,
    onAllMethods: () -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    val bgModifier = if (isHighlightedBlue) {
        Modifier
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PaperPilotPaymentColors.BlueDeep,
                        PaperPilotPaymentColors.BluePrimary,
                        PaperPilotPaymentColors.BlueBright
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.92f, size.height * 0.14f)
                )
            }
    } else {
        Modifier
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlightedBlue) Color.Transparent else PaperPilotPaymentColors.CardWhite
        ),
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.2.dp,
            color = when {
                isHighlightedBlue && isSelected -> Color(0xFF93C5FD)
                isHighlightedBlue -> PaperPilotPaymentColors.BlueBright.copy(alpha = 0.5f)
                isSelected -> PaperPilotPaymentColors.BluePrimary
                else -> PaperPilotPaymentColors.IceBorder
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .clickable(onClick = onSelectCard)
            .testTag(cardTestTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = if (isHighlightedBlue) Color(0xFFDBEAFE) else PaperPilotPaymentColors.BluePrimary,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = plan.title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isHighlightedBlue) Color.White else PaperPilotPaymentColors.BluePrimary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (isHighlightedBlue) Color.White else PaperPilotPaymentColors.IceSurface
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = PaperPilotPaymentColors.BlueDeep,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = priceLabel,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isHighlightedBlue) Color.White else PaperPilotPaymentColors.BlueDeep
                )
                Text(
                    text = "/ ${plan.billingPeriod}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isHighlightedBlue) Color(0xFFDBEAFE) else PaperPilotPaymentColors.TextMuted,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Text(
                text = subtitleLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighlightedBlue) Color.White.copy(alpha = 0.88f) else PaperPilotPaymentColors.TextMuted
            )

            HorizontalDivider(
                color = if (isHighlightedBlue) Color.White.copy(alpha = 0.22f) else PaperPilotPaymentColors.IceBorder
            )

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                plan.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (isHighlightedBlue) Color(0xFFBFDBFE) else PaperPilotPaymentColors.BluePrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHighlightedBlue) Color.White.copy(alpha = 0.95f) else PaperPilotPaymentColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = onSubscribeNow,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighlightedBlue) Color.White else PaperPilotPaymentColors.BluePrimary,
                    contentColor = if (isHighlightedBlue) PaperPilotPaymentColors.BlueDeep else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag(ctaTestTag)
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Subscribe Now",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = onAllMethods,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (isHighlightedBlue) Color.White.copy(alpha = 0.45f) else PaperPilotPaymentColors.IceBorder
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isHighlightedBlue) Color.White else PaperPilotPaymentColors.BluePrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Google Play Billing & All Payment Methods", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Material 3 blue-and-white card for PaperPilot's **₹1 for 1-Day Trial + UPI AutoPay** offer.
 * Clearly discloses the ₹1 immediate verification charge, recurring renewal price after 24 hours,
 * and one-tap AutoPay cancellation anytime before the trial ends.
 */
@Composable
fun TrialAutoPayOfferCard(
    selectedAutoPayPlan: MembershipPlan,
    isTrialCurrentlyActive: Boolean,
    autoPayEnabled: Boolean,
    trialExpiryMs: Long,
    onSelectAutoPayPlan: (MembershipPlan) -> Unit,
    onStartTrialAutoPay: () -> Unit,
    onManageAutoPay: () -> Unit
) {
    val targetPlan = if (selectedAutoPayPlan == MembershipPlan.YEARLY) MembershipPlan.YEARLY else MembershipPlan.MONTHLY
    val renewalLabel = "${targetPlan.priceDisplay}/${targetPlan.billingPeriod}"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PaperPilotPaymentColors.IceSurface),
        border = BorderStroke(2.dp, PaperPilotPaymentColors.BluePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_card_trial_1day_autopay")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Autorenew,
                        contentDescription = null,
                        tint = PaperPilotPaymentColors.BluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "1-DAY TRIAL + AUTOPAY",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = PaperPilotPaymentColors.BluePrimary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (isTrialCurrentlyActive) PaperPilotPaymentColors.VerifiedGreen else PaperPilotPaymentColors.BluePrimary
                ) {
                    Text(
                        text = if (isTrialCurrentlyActive) "TRIAL ACTIVE" else "TRY FOR ₹1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "₹1",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = PaperPilotPaymentColors.BlueDeep
                )
                Text(
                    text = "/ 1 day (24 hours full Premium)",
                    style = MaterialTheme.typography.titleSmall,
                    color = PaperPilotPaymentColors.TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (isTrialCurrentlyActive) {
                Text(
                    text = "Your 24-hour ad-free trial is active until ${formatPaymentDate(trialExpiryMs)}. AutoPay is currently ${if (autoPayEnabled) "ON ($renewalLabel)" else "OFF"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperPilotPaymentColors.TextPrimary
                )
                OutlinedButton(
                    onClick = onManageAutoPay,
                    shape = CircleShape,
                    border = BorderStroke(1.2.dp, PaperPilotPaymentColors.BluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("manage_trial_autopay_button")
                ) {
                    Icon(Icons.Rounded.Autorenew, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage Trial & AutoPay Settings", color = PaperPilotPaymentColors.BluePrimary, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Choose AutoPay plan after 1-day trial:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.BlueDeep
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = targetPlan == MembershipPlan.MONTHLY,
                        onClick = { onSelectAutoPayPlan(MembershipPlan.MONTHLY) },
                        label = { Text("₹99 / month", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PaperPilotPaymentColors.BluePrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("autopay_target_monthly_chip")
                    )
                    FilterChip(
                        selected = targetPlan == MembershipPlan.YEARLY,
                        onClick = { onSelectAutoPayPlan(MembershipPlan.YEARLY) },
                        label = { Text("₹999 / year", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PaperPilotPaymentColors.BluePrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("autopay_target_yearly_chip")
                    )
                }

                HorizontalDivider(color = PaperPilotPaymentColors.IceBorder)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MembershipPlan.TRIAL_1DAY.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = PaperPilotPaymentColors.BluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperPilotPaymentColors.TextPrimary
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PaperPilotPaymentColors.CardWhite,
                    border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AutoPay Disclosure: Pay ₹1 today to verify UPI AutoPay and unlock 24 hours of ad-free Premium. After 1 day, your subscription automatically renews at $renewalLabel unless you turn off AutoPay anytime in Membership Details before the trial ends.",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperPilotPaymentColors.TextMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Button(
                    onClick = onStartTrialAutoPay,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaperPilotPaymentColors.BlueDeep,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_trial_autopay_button")
                ) {
                    Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Start ₹1 Trial & Setup AutoPay",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
