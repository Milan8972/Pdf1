package com.example.ui.screens.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.LogoVariant
import com.example.ui.components.PaperPilotLogo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PaperPilotPaymentColors {
    val BlueDeep = Color(0xFF0B3B8C)
    val BluePrimary = Color(0xFF1565C0)
    val BlueBright = Color(0xFF1E88E5)
    val IceSurface = Color(0xFFF3F7FF)
    val IceBorder = Color(0xFFBFDBFE)
    val CardWhite = Color(0xFFFFFFFF)
    val VerifiedGreen = Color(0xFF0E9F6E)
    val WarningAmber = Color(0xFFD97706)
    val ErrorRed = Color(0xFFD92D20)
    val TextPrimary = Color(0xFF0F172A)
    val TextMuted = Color(0xFF475569)
}

fun formatPaymentDate(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(epochMs))
}

fun formatPaymentDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US).format(Date(epochMs))
}

@Composable
fun PaperPilotBlueHeroCard(
    title: String,
    subtitle: String,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PaperPilotPaymentColors.BlueDeep,
                        PaperPilotPaymentColors.BluePrimary,
                        PaperPilotPaymentColors.BlueBright
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PaperPilotLogo(size = 28.dp, variant = LogoVariant.BADGE)
                        }
                    }
                    Column {
                        Text(
                            text = "PaperPilot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "All-in-One PDF Editor",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDBEAFE)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
fun SecurePaymentBadge(
    label: String = "Server-Verified Checkout • 256-Bit TLS Encrypted • No Secrets Stored on Device",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperPilotPaymentColors.IceSurface,
        border = BorderStroke(1.dp, PaperPilotPaymentColors.IceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = "Secure payment",
                tint = PaperPilotPaymentColors.BluePrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = PaperPilotPaymentColors.BlueDeep,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = PaperPilotPaymentColors.VerifiedGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
