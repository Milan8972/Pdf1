package com.example.ui.screens.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.MembershipUiState

@Composable
fun PremiumGoogleAccountCard(
    membershipState: MembershipUiState,
    isSigningIn: Boolean,
    authError: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperPilotPaymentColors.CardWhite,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = PaperPilotPaymentColors.BluePrimary,
                    modifier = Modifier.size(30.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Account (Premium Membership)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PaperPilotPaymentColors.TextPrimary
                    )
                    Text(
                        text = if (membershipState.isSignedIn) {
                            membershipState.userEmail.ifBlank { "Signed in with Google" }
                        } else {
                            "Sign in with Google Mail to purchase, verify, and sync Premium."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperPilotPaymentColors.TextMuted
                    )
                }
                if (membershipState.isSignedIn) {
                    TextButton(onClick = onSignOut, modifier = Modifier.testTag("premium_signout_button")) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sign out")
                    }
                }
            }

            if (!membershipState.isSignedIn) {
                Button(
                    onClick = onSignIn,
                    enabled = !isSigningIn,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PaperPilotPaymentColors.BluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("premium_google_signin_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Signing in...")
                    } else {
                        Text("Login / Sign Up with Google Mail", fontWeight = FontWeight.Bold)
                    }
                }
            }

            val err = authError ?: membershipState.errorMessage
            if (!err.isNullOrBlank()) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.ErrorRed)
            }
        }
    }
}

@Composable
fun PremiumAccountShortcutsCard(
    membershipState: MembershipUiState,
    onOpenMembershipDetails: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperPilotPaymentColors.CardWhite,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            PremiumNavActionRow(
                icon = Icons.Rounded.CardMembership,
                title = "Membership Details & Cancellation",
                subtitle = "Status: ${membershipState.membershipStatus} • Expiry: ${formatPaymentDate(membershipState.premiumUntilEpochMs)}",
                testTag = "nav_membership_details",
                onClick = onOpenMembershipDetails
            )
            PremiumNavActionRow(
                icon = Icons.Rounded.ReceiptLong,
                title = "Transaction History & Receipts",
                subtitle = "${membershipState.transactions.size} verified payment records",
                testTag = "nav_transaction_history",
                onClick = onOpenTransactionHistory
            )
            PremiumNavActionRow(
                icon = Icons.Rounded.Restore,
                title = "Restore Purchases",
                subtitle = "Sync Google Play & server subscriptions",
                testTag = "nav_restore_purchases",
                onClick = onRestorePurchases
            )
            if (membershipState.isAdmin) {
                PremiumNavActionRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Admin Payment Dashboard",
                    subtitle = "Revenue, active plans, transactions & refunds",
                    testTag = "nav_admin_dashboard",
                    onClick = onOpenAdminDashboard
                )
            }
        }
    }
}

@Composable
fun PremiumRewardedAdBonusCard(
    onWatchRewardedAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperPilotPaymentColors.IceSurface,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Free Member Bonus — Watch Rewarded Ad",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PaperPilotPaymentColors.BlueDeep
            )
            Text(
                text = "Watch a short AdMob rewarded video to pause advertisements for 30 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = PaperPilotPaymentColors.TextMuted
            )
            OutlinedButton(
                onClick = onWatchRewardedAd,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("premium_watch_rewarded_ad_button")
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary)
                Spacer(Modifier.width(8.dp))
                Text("Watch Rewarded Ad (30m Ad-Free)", color = PaperPilotPaymentColors.BluePrimary)
            }
        }
    }
}

@Composable
private fun PremiumNavActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, tint = PaperPilotPaymentColors.BluePrimary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = PaperPilotPaymentColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PaperPilotPaymentColors.TextMuted)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = PaperPilotPaymentColors.TextMuted)
    }
}
