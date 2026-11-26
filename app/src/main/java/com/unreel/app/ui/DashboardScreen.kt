package com.unreel.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.unreel.app.R
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

    val isOverlayGranted = remember(context) { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isLocked = policy.strictLockMode && System.currentTimeMillis() < policy.settingsLockedUntilEpochMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_unreel_logo),
                            contentDescription = "Unreel logo",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Unreel", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { viewModel.toggleLockMode() },
                        modifier = Modifier.padding(end = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLocked) Color(0xFF4A2525) else Color(0xFF1F2937),
                            contentColor = if (isLocked) Color(0xFFFFB4AB) else Color(0xFF93C5FD)
                        )
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLocked) "Locked (Test Off)" else "Lock Off (Test)")
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                Text("Only restricted features trigger actions", style = MaterialTheme.typography.bodySmall)
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
                                Text("Lock Feed, Stories, Reels & Explore together", style = MaterialTheme.typography.bodySmall)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Blue = Selected • Grey = Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD)
                            )
                        }
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
                                        Text(
                                            text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) Color(0xFF8AB4F8) else Color(0xFF475569)
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1D4ED8),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF1E222B),
                                        labelColor = Color(0xFF94A3B8)
                                    )
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
                    "Single Reels sent in DMs remain playable; scrolling to next Reel returns to main feed.",
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
                val combinedMinutes = rule.sessionLimitMinutes.coerceIn(5, 60)
                val syncedDailyMinutes = (combinedMinutes * 3).coerceIn(15, 180)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Session & Daily Timer: ${combinedMinutes}m session / ${syncedDailyMinutes}m daily",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = combinedMinutes.toFloat(),
                        onValueChange = { selected ->
                            val sessionVal = selected.toInt().coerceIn(5, 60)
                            val dailyVal = (sessionVal * 3).coerceIn(15, 180)
                            onTimerChange(sessionVal, dailyVal)
                        },
                        valueRange = 5f..60f,
                        steps = 10
                    )
                }
            }
        }
    }
}
