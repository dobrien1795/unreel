package com.unreel.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.unreel.app.data.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val policy by viewModel.instagramPolicy.collectAsState()
    val usage by viewModel.usageSnapshot.collectAsState()
    val lockMessage by viewModel.lockBannerMessage.collectAsState()

    val isOverlayGranted = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Unreel", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.lockSettingsForHours(12) },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock 12h")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                PermissionCard(
                    context = context,
                    overlayGranted = isOverlayGranted.value,
                    onRefreshOverlay = { isOverlayGranted.value = Settings.canDrawOverlays(context) }
                )
            }

            if (lockMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lockMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearMessage() }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Target App: Instagram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("More apps supported in future releases", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = policy.enabled,
                                onCheckedChange = { viewModel.toggleMasterEnabled(it) }
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Block All Features", fontWeight = FontWeight.SemiBold)
                                Text("Immediately lock Feed, Stories, Reels & Explore", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = policy.blockAllFeatures,
                                onCheckedChange = { viewModel.toggleBlockAll(it) }
                            )
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Active Days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                val selected = policy.activeDays.contains(day)
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleDay(day) },
                                    label = {
                                        Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            items(AppFeature.entries.size) { index ->
                val feature = AppFeature.entries[index]
                val rule = policy.rules[feature] ?: FeatureRule(feature)
                val featureUsage = usage.byFeature[feature]

                FeatureControlCard(
                    feature = feature,
                    rule = rule,
                    usage = featureUsage,
                    disabledByMaster = !policy.enabled || policy.blockAllFeatures,
                    onModeChange = { viewModel.updateFeatureMode(feature, it) },
                    onTimerChange = { session, daily -> viewModel.updateFeatureTimers(feature, session, daily) }
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    context: Context,
    overlayGranted: Boolean,
    onRefreshOverlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Required System Permissions", fontWeight = FontWeight.Bold)
            Text(
                "Enable the Accessibility Service to enforce blocks inside Instagram and Overlay permission for countdown alerts.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text("Accessibility")
                }
                OutlinedButton(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                    onRefreshOverlay()
                }) {
                    Text(if (overlayGranted) "Overlays Ready" else "Enable Overlays")
                }
            }
        }
    }
}

@Composable
private fun FeatureControlCard(
    feature: AppFeature,
    rule: FeatureRule,
    usage: FeatureUsageState?,
    disabledByMaster: Boolean,
    onModeChange: (BlockMode) -> Unit,
    onTimerChange: (Int, Int) -> Unit
) {
    val usedMinsToday = (usage?.dailyUsedSeconds ?: 0L) / 60L

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(feature.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(feature.subtitle, style = MaterialTheme.typography.bodySmall)
                }
                if (rule.mode == BlockMode.TIMED) {
                    Text(
                        text = "${usedMinsToday}m / ${rule.dailyLimitMinutes}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (feature == AppFeature.REELS) {
                Text(
                    "Single Reels sent in DMs remain playable; scrolling to next Reel is blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BlockMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = rule.mode == mode,
                        onClick = { onModeChange(mode) },
                        enabled = !disabledByMaster,
                        shape = SegmentedButtonDefaults.itemShape(idx, BlockMode.entries.size)
                    ) {
                        Text(mode.label)
                    }
                }
            }

            if (rule.mode == BlockMode.TIMED && !disabledByMaster) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session Limit: ${rule.sessionLimitMinutes} min at a time", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = rule.sessionLimitMinutes.toFloat(),
                        onValueChange = { onTimerChange(it.toInt(), rule.dailyLimitMinutes) },
                        valueRange = 5f..60f,
                        steps = 10
                    )

                    Text("Daily Total Cap: ${rule.dailyLimitMinutes} min / day", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = rule.dailyLimitMinutes.toFloat(),
                        onValueChange = { onTimerChange(rule.sessionLimitMinutes, it.toInt()) },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }
        }
    }
}
