package com.example.ui.screens.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AdminDashboardMetrics
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import com.example.auth.RefundRecord
import com.example.viewmodel.MembershipUiState

/**
 * Screen 8: Transaction History with downloadable PDF receipts.
 */
@Composable
fun TransactionHistoryStep(
    transactions: List<PaymentTransaction>,
    refunds: List<RefundRecord>,
    onDownloadReceipt: (PaymentTransaction) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = "Transaction History",
                subtitle = "Verified payment records, subscription expiry dates, and downloadable PDF receipts.",
                badgeText = "${transactions.size} RECORDS"
            )
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PaperPilotPaymentColors.IceSurface,
                    border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = PaperPilotPaymentColors.BluePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Transactions Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PaperPilotPaymentColors.BlueDeep
                        )
                        Text(
                            text = "Completed payments, renewals, and refunds for your Google Mail account will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PaperPilotPaymentColors.TextMuted
                        )
                    }
                }
            }
        } else {
            items(transactions, key = { it.id.ifBlank { it.transactionId } }) { tx ->
                TransactionRecordCard(
                    tx = tx,
                    onDownloadReceipt = { onDownloadReceipt(tx) }
                )
            }
        }

        if (refunds.isNotEmpty()) {
            item {
                Text(
                    text = "Refunds (${refunds.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.BlueDeep
                )
            }
            items(refunds, key = { it.id.ifBlank { it.refundId } }) { ref ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PaperPilotPaymentColors.CardWhite,
                    border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Refund ₹${ref.amountInr}", fontWeight = FontWeight.Bold, color = PaperPilotPaymentColors.WarningAmber)
                            Text(ref.status, style = MaterialTheme.typography.labelSmall, color = PaperPilotPaymentColors.TextMuted)
                        }
                        Text("Tx: ${ref.transactionId}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                        Text("Processed: ${formatPaymentDateTime(ref.processedAtMs)}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                        if (ref.reason.isNotBlank()) {
                            Text("Reason: ${ref.reason}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRecordCard(
    tx: PaymentTransaction,
    onDownloadReceipt: () -> Unit
) {
    val statusColor = when (tx.status) {
        "SUCCESS" -> PaperPilotPaymentColors.VerifiedGreen
        "REFUNDED" -> PaperPilotPaymentColors.WarningAmber
        else -> PaperPilotPaymentColors.ErrorRed
    }
    val planTitle = MembershipPlan.fromTierId(tx.planTier)?.title ?: tx.planTier

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PaperPilotPaymentColors.CardWhite,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(planTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PaperPilotPaymentColors.TextPrimary)
                    Text(formatPaymentDateTime(tx.purchaseDateMs), style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${tx.amountInr}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = PaperPilotPaymentColors.BlueDeep)
                    Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.14f)) {
                        Text(
                            text = tx.status.ifBlank { "UNKNOWN" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = PaperPilotPaymentColors.IceBorder)
            Text("Transaction ID: ${tx.transactionId.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
            Text("Provider: ${tx.paymentProvider} • ${tx.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
            Text("Valid Until: ${formatPaymentDate(tx.expiryDateMs)}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)

            if (tx.status == "SUCCESS" || tx.status == "REFUNDED") {
                OutlinedButton(
                    onClick = onDownloadReceipt,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download PDF Receipt", color = PaperPilotPaymentColors.BluePrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Screen 9: Membership Details.
 */
@Composable
fun MembershipDetailsStep(
    membershipState: MembershipUiState,
    latestTransaction: PaymentTransaction?,
    isRestoring: Boolean,
    isUpdatingAutoPay: Boolean = false,
    onToggleAutoPay: (Boolean) -> Unit = {},
    onUpgradeOrRenew: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCancel: () -> Unit,
    onDownloadReceipt: (PaymentTransaction) -> Unit
) {
    val autoPayTarget = MembershipPlan.fromTierId(membershipState.autoPayTargetPlan)
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = if (membershipState.isPremiumActive) {
                    "${membershipState.activePlan?.title ?: "Premium"} Active"
                } else {
                    "Free Plan (Ad-Supported)"
                },
                subtitle = if (membershipState.isPremiumActive) {
                    if (membershipState.isTrial) {
                        "Your ₹1 24-hour Premium trial is active and all AdMob ads are disabled."
                    } else {
                        "All AdMob ads are disabled and Premium PDF tools are unlocked."
                    }
                } else {
                    "Start with ₹1 for a 1-day trial or upgrade to Monthly (₹99) / Yearly (₹999) Premium."
                },
                badgeText = if (membershipState.isTrial) "₹1 TRIAL" else membershipState.membershipStatus.ifBlank { "FREE" }
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PaperPilotPaymentColors.CardWhite,
                border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
                    DetailLine("Account", membershipState.userEmail.ifBlank { "Not signed in" })
                    DetailLine("Current Plan", membershipState.activePlan?.let { "${it.title} (${it.priceDisplay}/${it.billingPeriod})" } ?: "Free Plan")
                    DetailLine("Status", membershipState.membershipStatus)
                    DetailLine("Expiry Date", formatPaymentDate(membershipState.premiumUntilEpochMs))
                    DetailLine(
                        "UPI AutoPay",
                        when {
                            membershipState.autoPayEnabled && autoPayTarget != null ->
                                "Enabled (${autoPayTarget.priceDisplay}/${autoPayTarget.billingPeriod})"
                            membershipState.autoPayEnabled -> "Enabled"
                            else -> "Off"
                        }
                    )
                    if (membershipState.autoPayEnabled && membershipState.nextBillingAmountInr > 0L) {
                        DetailLine("Next AutoPay Charge", "₹${membershipState.nextBillingAmountInr} INR")
                    }
                    if (membershipState.autoPayMandateId.isNotBlank()) {
                        DetailLine("AutoPay Mandate ID", membershipState.autoPayMandateId)
                    }
                    DetailLine("Payment Provider", membershipState.paymentProvider.ifBlank { "—" })
                    DetailLine("Last Transaction ID", membershipState.lastTransactionId.ifBlank { "—" })
                    DetailLine("AdMob Advertisements", if (membershipState.isPremiumActive) "Disabled (Ad-Free)" else "Enabled")
                }
            }
        }

        if (membershipState.isPremiumActive && (membershipState.autoPayEnabled || membershipState.isTrial)) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PaperPilotPaymentColors.IceSurface,
                    border = BorderStroke(1.2.dp, PaperPilotPaymentColors.BluePrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Autorenew, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                                Column {
                                    Text(
                                        text = "UPI AutoPay Renewal",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PaperPilotPaymentColors.BlueDeep
                                    )
                                    Text(
                                        text = if (membershipState.autoPayEnabled) {
                                            "Active • Renews at ${autoPayTarget?.priceDisplay ?: "₹${membershipState.nextBillingAmountInr}"}/${autoPayTarget?.billingPeriod ?: "cycle"}"
                                        } else {
                                            "Turned off • No automatic charge will occur"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PaperPilotPaymentColors.TextMuted
                                    )
                                }
                            }
                            Switch(
                                checked = membershipState.autoPayEnabled,
                                enabled = !isUpdatingAutoPay && membershipState.autoPayEnabled,
                                onCheckedChange = { checked -> onToggleAutoPay(checked) },
                                colors = SwitchDefaults.colors(checkedTrackColor = PaperPilotPaymentColors.BluePrimary),
                                modifier = Modifier.testTag("autopay_status_switch")
                            )
                        }

                        if (membershipState.autoPayEnabled) {
                            OutlinedButton(
                                onClick = { onToggleAutoPay(false) },
                                enabled = !isUpdatingAutoPay,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, PaperPilotPaymentColors.WarningAmber),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("toggle_autopay_button")
                            ) {
                                if (isUpdatingAutoPay) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Updating AutoPay...")
                                } else {
                                    Text("Turn Off AutoPay (Keep Current Access)", color = PaperPilotPaymentColors.BlueDeep, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onUpgradeOrRenew,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (membershipState.isPremiumActive) "Renew or Switch Plan" else "Upgrade to Premium", fontWeight = FontWeight.Bold)
            }
        }

        item {
            OutlinedButton(
                onClick = onRestorePurchases,
                enabled = !isRestoring,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("restore_purchases_button")
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restoring Purchases...")
                } else {
                    Icon(Icons.Rounded.Restore, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Purchases", color = PaperPilotPaymentColors.BluePrimary)
                }
            }
        }

        if (latestTransaction != null) {
            item {
                OutlinedButton(
                    onClick = { onDownloadReceipt(latestTransaction) },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Latest PDF Receipt", color = PaperPilotPaymentColors.BluePrimary)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenHistory,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("View Transaction History", color = PaperPilotPaymentColors.BluePrimary)
            }
        }

        if (membershipState.isPremiumActive) {
            item {
                OutlinedButton(
                    onClick = onOpenCancel,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, PaperPilotPaymentColors.ErrorRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_cancel_subscription_button")
                ) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null, tint = PaperPilotPaymentColors.ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel Subscription", color = PaperPilotPaymentColors.ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PaperPilotPaymentColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PaperPilotPaymentColors.TextPrimary)
    }
}

/**
 * Screen 10: Cancel Subscription.
 */
@Composable
fun CancelSubscriptionStep(
    membershipState: MembershipUiState,
    isCancelling: Boolean,
    onConfirmCancel: (reason: String) -> Unit,
    onOpenPlaySubscriptions: () -> Unit,
    onKeepPremium: () -> Unit
) {
    val reasons = listOf(
        "I no longer need PDF editing frequently",
        "Switching between Monthly and Yearly plan",
        "Price is higher than expected",
        "Temporary break — may resubscribe later"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFFFFF7ED),
                border = BorderStroke(1.dp, PaperPilotPaymentColors.WarningAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = PaperPilotPaymentColors.WarningAmber)
                        Text("Before You Cancel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Your Premium ad-free benefits remain active until ${formatPaymentDate(membershipState.premiumUntilEpochMs)}. Once expired, your account automatically returns to the Free plan and AdMob advertisements will resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperPilotPaymentColors.TextMuted
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PaperPilotPaymentColors.CardWhite,
                border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Tell us why you're cancelling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = PaperPilotPaymentColors.BluePrimary)
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium, color = PaperPilotPaymentColors.TextPrimary)
                        }
                    }
                }
            }
        }

        if (membershipState.paymentProvider == "GOOGLE_PLAY") {
            item {
                OutlinedButton(
                    onClick = onOpenPlaySubscriptions,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Manage Subscription in Google Play Store", color = PaperPilotPaymentColors.BluePrimary)
                }
            }
        }

        item {
            Button(
                onClick = { onConfirmCancel(selectedReason) },
                enabled = !isCancelling,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.ErrorRed),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_cancel_subscription_button")
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelling...")
                } else {
                    Text("Confirm Cancellation", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onKeepPremium,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Keep My Premium Membership", color = PaperPilotPaymentColors.BluePrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Screen 11: Authorized Administrator Payment Dashboard.
 * Strictly unreachable for non-admin users.
 */
@Composable
fun AdminDashboardStep(
    metrics: AdminDashboardMetrics?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    var statusFilter by remember { mutableStateOf("ALL") }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PaperPilotBlueHeroCard(
                title = "Admin Payment Console",
                subtitle = "Authorized administrator view across users/, memberships/, payments/, and refunds/.",
                badgeText = "ADMIN ONLY"
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                    Text("Live Revenue & Membership KPIs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRefresh, enabled = !isLoading, shape = CircleShape) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
        }

        if (isLoading && metrics == null) {
            item {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    CircularProgressIndicator(color = PaperPilotPaymentColors.BluePrimary)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                Text(errorMessage, color = PaperPilotPaymentColors.ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (metrics != null) {
            val kpiRows = listOf(
                ("Total Gross Revenue" to "₹${metrics.totalGrossRevenueInr}") to ("Total Registered Users" to "${metrics.totalRegisteredUsers}"),
                ("Monthly Revenue (₹99)" to "₹${metrics.monthlyRevenueInr}") to ("Yearly Revenue (₹999)" to "₹${metrics.yearlyRevenueInr}"),
                ("Total Premium Members" to "${metrics.totalPremiumMembers}") to ("Expired Memberships" to "${metrics.expiredMemberships}"),
                ("Active Monthly Members" to "${metrics.activeMonthlyMembers}") to ("Active Yearly Members" to "${metrics.activeYearlyMembers}"),
                ("Successful Payments" to "${metrics.successfulPayments}") to ("Failed Transactions" to "${metrics.failedTransactions}")
            )
            items(kpiRows) { (left, right) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminKpiCard(left.first, left.second, Modifier.weight(1f))
                    AdminKpiCard(right.first, right.second, Modifier.weight(1f))
                }
            }
            item {
                AdminKpiCard("Total Refunds Processed", "${metrics.refundsCount}", Modifier.fillMaxWidth())
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "SUCCESS", "FAILED", "REFUNDED").forEach { tag ->
                        FilterChip(
                            selected = statusFilter == tag,
                            onClick = { statusFilter = tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            val filteredTx = metrics.recentTransactions.filter {
                statusFilter == "ALL" || it.status == statusFilter
            }
            item {
                Text(
                    text = "Transaction Records (${filteredTx.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PaperPilotPaymentColors.BlueDeep
                )
            }
            if (filteredTx.isEmpty()) {
                item {
                    Text(
                        text = "No matching transaction records found in Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperPilotPaymentColors.TextMuted
                    )
                }
            } else {
                items(filteredTx, key = { it.id.ifBlank { it.transactionId } }) { tx ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PaperPilotPaymentColors.CardWhite,
                        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("${tx.planTier} • ₹${tx.amountInr}", fontWeight = FontWeight.Bold, color = PaperPilotPaymentColors.BlueDeep)
                                Text(tx.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text("User UID: ${tx.userId}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                            Text("Tx ID: ${tx.transactionId} (${tx.paymentProvider})", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                            Text("Date: ${formatPaymentDateTime(tx.purchaseDateMs)} • Exp: ${formatPaymentDate(tx.expiryDateMs)}", style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PaperPilotPaymentColors.IceSurface,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = PaperPilotPaymentColors.TextMuted)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = PaperPilotPaymentColors.BlueDeep)
        }
    }
}
