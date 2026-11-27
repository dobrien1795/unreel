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
import kotlinx.coroutines.delay
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

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

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
                                Text("Only features set in Unreel are enforced", style = MaterialTheme.typography.bodySmall)
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
                                Text("Only enable when you want all features blocked", style = MaterialTheme.typography.bodySmall)
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                val selected = policy.activeDays.contains(day)
                                val firstLetter = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()).take(1)
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleDay(day) },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = firstLetter,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
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
                    nowMs = nowMs,
                    disabledByMaster = !policy.enabled || policy.blockAllFeatures,
                    onModeChange = { viewModel.updateFeatureMode(feature, it) },
                    onSessionTimerChange = { mins -> viewModel.updateSessionTimer(feature, mins) },
                    onDailyUsesChange = { uses -> viewModel.updateDailyUses(feature, uses) }
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
    nowMs: Long,
    disabledByMaster: Boolean,
    onModeChange: (BlockMode) -> Unit,
    onSessionTimerChange: (Int) -> Unit,
    onDailyUsesChange: (Int) -> Unit
) {
    val sessionCapSeconds = rule.sessionLimitMinutes.coerceAtLeast(1) * 60L
    val sessionUsedSeconds = (usage?.currentSessionUsedSeconds ?: 0L).coerceAtLeast(0L)
    val sessionLeftSeconds = (sessionCapSeconds - sessionUsedSeconds).coerceAtLeast(0L)
    val cooldownLeftSeconds = if ((usage?.cooldownUntilEpochMs ?: 0L) > nowMs) {
        ((usage!!.cooldownUntilEpochMs - nowMs) / 1000L).coerceAtLeast(0L)
    } else {
        0L
    }

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
                var draftSessionMins by remember(feature, rule.sessionLimitMinutes) {
                    mutableFloatStateOf(rule.sessionLimitMinutes.coerceIn(1, 60).toFloat())
                }
                var draftUsesPerDay by remember(feature, rule.dailyUsesAllowed) {
                    mutableFloatStateOf(rule.dailyUsesAllowed.coerceIn(1, 8).toFloat())
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Session Timer: ${draftSessionMins.toInt()}m per session",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = draftSessionMins,
                        onValueChange = { draftSessionMins = it },
                        onValueChangeFinished = {
                            onSessionTimerChange(draftSessionMins.toInt().coerceIn(1, 60))
                        },
                        valueRange = 1f..60f
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Times Allowed in 24h: ${draftUsesPerDay.toInt()}× (${rule.cooldownMinutes}m break between sessions)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = draftUsesPerDay,
                        onValueChange = { draftUsesPerDay = it },
                        onValueChangeFinished = {
                            onDailyUsesChange(draftUsesPerDay.toInt().coerceIn(1, 8))
                        },
                        valueRange = 1f..8f,
                        steps = 6
                    )
                }
            }

            if (rule.mode == BlockMode.BLOCK || rule.mode == BlockMode.TIMED) {
                val statusText = when {
                    rule.mode == BlockMode.BLOCK ->
                        "Session left: 0:00 (Blocked in settings)"
                    cooldownLeftSeconds > 0L ->
                        "Session left: 0:00 • Break left: ${formatDuration(cooldownLeftSeconds)}"
                    else ->
                        "Session left: ${formatDuration(sessionLeftSeconds)} • Uses left today: ${(rule.dailyUsesAllowed - (usage?.sessionsUsedToday ?: 0)).coerceAtLeast(0)}/${rule.dailyUsesAllowed}"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rule.mode == BlockMode.BLOCK || cooldownLeftSeconds > 0L) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val mins = safe / 60L
    val secs = safe % 60L
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}
