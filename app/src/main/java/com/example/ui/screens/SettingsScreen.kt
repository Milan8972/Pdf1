package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.TintScan
import com.example.viewmodel.MembershipUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    membershipState: MembershipUiState = MembershipUiState(),
    onOpenPremium: () -> Unit = {},
    onWatchRewardedAd: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentPreset) -> Unit,
    onSetDefaultSaveFolder: (String) -> Unit,
    onSetOcrLanguage: (String) -> Unit,
    onSetDefaultImageQuality: (Int) -> Unit,
    onSetKeepOriginalFiles: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalPaperPilotColors.current
    var showOcrLangDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var infoModal by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Premium Membership (Google Mail Login/Sign-Up ONLY for Premium)
        item {
            SettingsGroup(title = "Premium Membership (Ad-Free)") {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPremium)
                            .padding(16.dp)
                            .testTag("settings_premium_card")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (membershipState.isPremiumActive) TintScan.copy(alpha = 0.16f)
                                    else BrandAmber.copy(alpha = 0.18f)
                                )
                        ) {
                            Icon(
                                imageVector = if (membershipState.isPremiumActive) Icons.Rounded.Verified else Icons.Rounded.WorkspacePremium,
                                contentDescription = "Premium Membership",
                                tint = if (membershipState.isPremiumActive) TintScan else BrandCoral,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (membershipState.isPremiumActive) {
                                    "Premium Active • Ad-Free"
                                } else {
                                    "Upgrade to Premium (Remove Ads)"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when {
                                    membershipState.isPremiumActive ->
                                        "${membershipState.userEmail} • ${membershipState.activePlan?.priceDisplay ?: ""}/${membershipState.activePlan?.billingPeriod ?: ""}"
                                    membershipState.isSignedIn ->
                                        "${membershipState.userEmail} • ₹99/month or ₹999/year"
                                    else ->
                                        "Login / Sign up with Google Mail for ₹99/month or ₹999/year"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.mutedText
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!membershipState.isPremiumActive) {
                        SettingsClickRow(
                            icon = Icons.Rounded.PlayCircle,
                            title = "Watch Rewarded Ad",
                            value = "30m Ad-Free",
                            onClick = onWatchRewardedAd
                        )
                    }
                }
            }
        }

        // Appearance
        item {
            SettingsGroup(title = "Appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Dark mode", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Text("Accent color", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val selected = settings.accentPreset == preset
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(preset.swatch)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { onSetAccentPreset(preset) }
                                    .testTag("settings_accent_${preset.name.lowercase()}")
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "${preset.label} selected", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preferences
        item {
            SettingsGroup(title = "Preferences") {
                Column {
                    SettingsToggleRow(
                        title = "Keep original files",
                        subtitle = "Always export processed or edited PDFs as new copies",
                        checked = settings.keepOriginalFiles,
                        onCheckedChange = onSetKeepOriginalFiles
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Folder,
                        title = "Default save folder",
                        value = settings.defaultSaveFolder,
                        onClick = { showFolderDialog = true }
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Translate,
                        title = "OCR language",
                        value = settings.ocrLanguage,
                        onClick = { showOcrLangDialog = true }
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Default image quality", style = MaterialTheme.typography.titleSmall)
                            Text("${settings.defaultImageQuality}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = settings.defaultImageQuality.toFloat(),
                            onValueChange = { onSetDefaultImageQuality(it.roundToInt()) },
                            valueRange = 40f..100f
                        )
                    }
                }
            }
        }

        // About
        item {
            SettingsGroup(title = "About") {
                Column {
                    SettingsClickRow(Icons.Rounded.Shield, "Terms of service", "") {
                        infoModal = "Terms of Service" to "paperpilot processes your documents locally on your Android device. You retain full ownership and responsibility for all files created, merged, signed, or shared."
                    }
                    SettingsClickRow(Icons.Rounded.PrivacyTip, "Privacy policy", "100% On-device") {
                        infoModal = "Privacy Policy" to "Private by design: paperpilot requires no account and never uploads your PDFs, scanned images, or signatures to any server. All document processing, thumbnails, and settings stay strictly on your device."
                    }
                    SettingsClickRow(Icons.Rounded.HelpOutline, "Help & Contact", "") {
                        infoModal = "Help & Support" to "Need assistance with paperpilot?\n\n• Every tool follows: Pick files → Options → Process → Save/Share.\n• Protected PDFs store a local SHA-256 lock.\n• Re-run the quick tour anytime below."
                    }
                    SettingsClickRow(Icons.Rounded.Info, "Open-source credits", "") {
                        infoModal = "Open-Source Credits" to "Built natively for Android using:\n• Jetpack Compose & Material 3\n• AndroidX CameraX, Room, DataStore\n• Android PdfRenderer & PdfDocument APIs\n• Coil Image Loader\n• Space Grotesk & Inter Typography"
                    }
                    SettingsClickRow(Icons.Rounded.Info, "Replay onboarding tour", "") {
                        onReplayOnboarding()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App version", style = MaterialTheme.typography.bodyMedium, color = extra.mutedText)
                        Text("paperpilot 1.0.0", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showOcrLangDialog) {
        val languages = listOf("English", "Spanish", "French", "German", "Italian", "Portuguese", "Hindi", "Japanese")
        AlertDialog(
            onDismissRequest = { showOcrLangDialog = false },
            title = { Text("OCR Language") },
            text = {
                Column {
                    languages.forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetOcrLanguage(lang); showOcrLangDialog = false }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(lang, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (settings.ocrLanguage == lang) {
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOcrLangDialog = false }) { Text("Done") } }
        )
    }

    if (showFolderDialog) {
        val folders = listOf("Documents/paperpilot", "Downloads/paperpilot", "Internal/Workspaces")
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Default Save Folder") },
            text = {
                Column {
                    folders.forEach { f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetDefaultSaveFolder(f); showFolderDialog = false }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(f, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (settings.defaultSaveFolder == f) {
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Done") } }
        )
    }

    infoModal?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { infoModal = null },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { infoModal = null }) { Text("Got it") } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPaperPilotColors.current.mutedText)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = LocalPaperPilotColors.current.mutedText)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
