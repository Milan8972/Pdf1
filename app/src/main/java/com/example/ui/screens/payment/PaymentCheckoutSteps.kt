package com.example.ui.screens.payment

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import com.example.payment.PaymentFlowUiState
import com.example.payment.PaymentGatewayProvider
import com.example.payment.PaymentMethodOption

/**
 * Screen 2: Select Plan (Monthly ₹99/month vs Yearly ₹999/year).
 */
@Composable
fun SelectPlanStep(
    selectedPlan: MembershipPlan,
    activePlan: MembershipPlan?,
    playStorePrices: Map<MembershipPlan, String>,
    onSelectPlan: (MembershipPlan) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = "Choose Your Premium Plan",
                subtitle = "Transparent Indian Rupee pricing. Zero hidden fees. Cancel anytime.",
                badgeText = "STEP 1 OF 3"
            )
        }

        items(MembershipPlan.paidPlans) { plan ->
            val isSelected = selectedPlan == plan
            val isCurrent = activePlan == plan
            val priceLabel = playStorePrices[plan] ?: plan.priceDisplay

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isSelected) PaperPilotPaymentColors.IceSurface else PaperPilotPaymentColors.CardWhite,
                border = BorderStroke(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) PaperPilotPaymentColors.BluePrimary else PaperPilotPaymentColors.IceBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPlan(plan) }
                    .testTag("plan_card_${plan.name.lowercase()}")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectPlan(plan) },
                                colors = RadioButtonDefaults.colors(selectedColor = PaperPilotPaymentColors.BluePrimary)
                            )
                            Column {
                                Text(
                                    text = plan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PaperPilotPaymentColors.TextPrimary
                                )
                                Text(
                                    text = if (plan == MembershipPlan.YEARLY) "Billed yearly • ₹83.25/month" else "Billed monthly • 30-day cycle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PaperPilotPaymentColors.TextMuted
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isCurrent) PaperPilotPaymentColors.VerifiedGreen.copy(alpha = 0.14f)
                            else if (plan == MembershipPlan.YEARLY) PaperPilotPaymentColors.BluePrimary
                            else PaperPilotPaymentColors.IceSurface
                        ) {
                            Text(
                                text = if (isCurrent) "Active" else plan.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isCurrent -> PaperPilotPaymentColors.VerifiedGreen
                                    plan == MembershipPlan.YEARLY -> Color.White
                                    else -> PaperPilotPaymentColors.BlueDeep
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = priceLabel,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = PaperPilotPaymentColors.BlueDeep
                        )
                        Text(
                            text = "/ ${plan.billingPeriod}",
                            style = MaterialTheme.typography.titleSmall,
                            color = PaperPilotPaymentColors.TextMuted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    HorizontalDivider(color = PaperPilotPaymentColors.IceBorder)

                    plan.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = PaperPilotPaymentColors.BluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperPilotPaymentColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        item { SecurePaymentBadge() }

        item {
            Button(
                onClick = onContinue,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_plan_continue_button")
            ) {
                Text(
                    text = "Continue with ${selectedPlan.title} (${selectedPlan.priceDisplay}/${selectedPlan.billingPeriod})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Screen 3: Payment Method (Google Play Billing primary + India enrolled alternative billing via Razorpay).
 */
@Composable
fun PaymentMethodStep(
    selectedPlan: MembershipPlan,
    selectedMethod: PaymentMethodOption,
    onSelectMethod: (PaymentMethodOption) -> Unit,
    onContinueToSummary: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = "Select Payment Method",
                subtitle = "${selectedPlan.title} • Exact total: ${selectedPlan.priceDisplay}/${selectedPlan.billingPeriod} (INR)",
                badgeText = "STEP 2 OF 3"
            )
        }

        item {
            Text(
                text = "Primary (Google Play Policy Compliant)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PaperPilotPaymentColors.BlueDeep
            )
        }

        item {
            PaymentMethodRow(
                option = PaymentMethodOption.GOOGLE_PLAY_BILLING,
                icon = Icons.Rounded.Shop,
                selected = selectedMethod == PaymentMethodOption.GOOGLE_PLAY_BILLING,
                badge = "Recommended",
                onClick = { onSelectMethod(PaymentMethodOption.GOOGLE_PLAY_BILLING) }
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "India Alternative Billing (Razorpay • Enrolled Developers)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.BlueDeep
                )
                Text(
                    text = "Available under Google Play's India Alternative Billing / User Choice Billing programme. Orders are created and cryptographically verified server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperPilotPaymentColors.TextMuted
                )
            }
        }

        val razorpayOptions = PaymentMethodOption.entries.filter { it.provider == PaymentGatewayProvider.RAZORPAY }
        items(razorpayOptions) { option ->
            val icon = when (option) {
                PaymentMethodOption.RAZORPAY_UPI,
                PaymentMethodOption.RAZORPAY_GPAY,
                PaymentMethodOption.RAZORPAY_PHONEPE -> Icons.Rounded.PhoneAndroid
                PaymentMethodOption.RAZORPAY_DEBIT_CARD,
                PaymentMethodOption.RAZORPAY_CREDIT_CARD -> Icons.Rounded.CreditCard
                PaymentMethodOption.RAZORPAY_NET_BANKING -> Icons.Rounded.AccountBalance
                else -> Icons.Rounded.Payment
            }
            PaymentMethodRow(
                option = option,
                icon = icon,
                selected = selectedMethod == option,
                badge = null,
                onClick = { onSelectMethod(option) }
            )
        }

        item {
            Button(
                onClick = onContinueToSummary,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("payment_method_continue_button")
            ) {
                Text("Review Order Summary", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PaymentMethodRow(
    option: PaymentMethodOption,
    icon: ImageVector,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) PaperPilotPaymentColors.IceSurface else PaperPilotPaymentColors.CardWhite,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) PaperPilotPaymentColors.BluePrimary else PaperPilotPaymentColors.IceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("payment_method_${option.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PaperPilotPaymentColors.BluePrimary.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = option.title, tint = PaperPilotPaymentColors.BluePrimary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperPilotPaymentColors.TextPrimary
                    )
                    if (badge != null) {
                        Surface(shape = CircleShape, color = PaperPilotPaymentColors.VerifiedGreen.copy(alpha = 0.15f)) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = PaperPilotPaymentColors.VerifiedGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperPilotPaymentColors.TextMuted
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = PaperPilotPaymentColors.BluePrimary)
            )
        }
    }
}

/**
 * Screen 4: Order Summary.
 */
@Composable
fun OrderSummaryStep(
    flowState: PaymentFlowUiState,
    userEmail: String,
    isSignedIn: Boolean,
    onSignInFirst: () -> Unit,
    onConfirmAndPay: () -> Unit,
    onChangeMethod: () -> Unit
) {
    val plan = flowState.selectedPlan
    val method = flowState.selectedMethod
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = "Order Summary",
                subtitle = "Verify your plan, exact INR total, and payment gateway before proceeding.",
                badgeText = "STEP 3 OF 3"
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PaperPilotPaymentColors.CardWhite,
                border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(18.dp)) {
                    SummaryRow("Membership Plan", plan.title)
                    SummaryRow(
                        "Billing Cycle",
                        when (plan) {
                            MembershipPlan.TRIAL_1DAY -> "1 Day Trial (24 hours) + AutoPay"
                            MembershipPlan.YEARLY -> "1 Year (365 days)"
                            MembershipPlan.MONTHLY -> "1 Month (30 days)"
                        }
                    )
                    SummaryRow("Account (Google Mail)", if (isSignedIn) userEmail else "Sign in required")
                    SummaryRow("Payment Gateway", method.provider.label)
                    SummaryRow("Payment Method", method.title)
                    SummaryRow("Currency", "INR (₹)")
                    HorizontalDivider(color = PaperPilotPaymentColors.IceBorder)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Total Payable",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PaperPilotPaymentColors.TextPrimary
                        )
                        Text(
                            text = "${plan.priceDisplay}.00",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = PaperPilotPaymentColors.BlueDeep
                        )
                    }
                }
            }
        }

        item {
            SecurePaymentBadge(
                label = if (method.provider == PaymentGatewayProvider.GOOGLE_PLAY) {
                    "Processed via official Google Play Billing & verified server-side before activation."
                } else {
                    "India Alternative Billing via Razorpay: order created on secure backend & HMAC-SHA256 verified before activation."
                }
            )
        }

        item {
            Button(
                onClick = if (isSignedIn) onConfirmAndPay else onSignInFirst,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("order_summary_pay_button")
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSignedIn) "Pay ${plan.priceDisplay}.00 Securely" else "Sign in with Google Mail to Pay",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onChangeMethod,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Change Payment Method", color = PaperPilotPaymentColors.BluePrimary)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PaperPilotPaymentColors.TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = PaperPilotPaymentColors.TextPrimary
        )
    }
}

/**
 * Screen 5: Payment Processing.
 */
@Composable
fun PaymentProcessingStep(
    stageText: String,
    plan: MembershipPlan,
    method: PaymentMethodOption
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PaperPilotPaymentColors.IceSurface,
            border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(28.dp)
            ) {
                CircularProgressIndicator(
                    color = PaperPilotPaymentColors.BluePrimary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Verifying Your Payment",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.BlueDeep
                )
                Text(
                    text = stageText.ifBlank { "Creating secure server order and verifying payment signature..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperPilotPaymentColors.TextMuted,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${plan.title} • ${plan.priceDisplay}/${plan.billingPeriod} via ${method.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = PaperPilotPaymentColors.BluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
                SecurePaymentBadge("Do not close the app while server verification completes.")
            }
        }
    }
}

/**
 * Screen 6: Payment Successful.
 */
@Composable
fun PaymentSuccessfulStep(
    transaction: PaymentTransaction?,
    onDownloadReceipt: (PaymentTransaction) -> Unit,
    onViewMembership: () -> Unit,
    onDone: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PaperPilotPaymentColors.IceSurface,
                border = BorderStroke(1.5.dp, PaperPilotPaymentColors.VerifiedGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Payment Verified",
                        tint = PaperPilotPaymentColors.VerifiedGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Payment Successful!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PaperPilotPaymentColors.BlueDeep
                    )
                    Text(
                        text = "Your PaperPilot Premium membership is now verified and active. All AdMob advertisements have been disabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperPilotPaymentColors.TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (transaction != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PaperPilotPaymentColors.CardWhite,
                    border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
                        SummaryRow("Receipt #", transaction.receiptNumber.ifBlank { "—" })
                        SummaryRow("Transaction ID", transaction.transactionId.ifBlank { "—" })
                        SummaryRow("Amount Paid", "₹${transaction.amountInr}.00 INR")
                        SummaryRow("Plan", MembershipPlan.fromTierId(transaction.planTier)?.title ?: transaction.planTier)
                        SummaryRow("Provider", transaction.paymentProvider)
                        SummaryRow("Purchase Date", formatPaymentDateTime(transaction.purchaseDateMs))
                        SummaryRow("Valid Until", formatPaymentDate(transaction.expiryDateMs))
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onDownloadReceipt(transaction) },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("download_success_receipt_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Download PDF Receipt", color = PaperPilotPaymentColors.BluePrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Button(
                onClick = onDone,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("payment_success_done_button")
            ) {
                Text("Start Using PaperPilot Ad-Free", fontWeight = FontWeight.Bold)
            }
        }

        item {
            OutlinedButton(
                onClick = onViewMembership,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("View Membership Details", color = PaperPilotPaymentColors.BluePrimary)
            }
        }
    }
}

/**
 * Screen 7: Payment Failed.
 */
@Composable
fun PaymentFailedStep(
    title: String,
    reason: String,
    onRetry: () -> Unit,
    onChangeMethod: () -> Unit,
    onBackToOverview: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PaperPilotPaymentColors.CardWhite,
            border = BorderStroke(1.5.dp, PaperPilotPaymentColors.ErrorRed.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Payment Failed",
                    tint = PaperPilotPaymentColors.ErrorRed,
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = title.ifBlank { "Payment Not Completed" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.TextPrimary
                )
                Text(
                    text = reason.ifBlank { "Payment could not be verified. Your membership has not been activated." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperPilotPaymentColors.TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onRetry,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("payment_failed_retry_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Payment", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onChangeMethod,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Choose Another Payment Method", color = PaperPilotPaymentColors.BluePrimary)
                }
                OutlinedButton(
                    onClick = onBackToOverview,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Back to Premium Overview")
                }
            }
        }
    }
}
