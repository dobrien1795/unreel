package com.unreel.app.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable
enum class AppFeature(val displayName: String, val subtitle: String) {
    HOME_AND_STORIES("Home Feed & Stories", "Main scrolling feed and top story tray"),
    REELS("Reels", "Short-form vertical video feed (DM single-clip safe)"),
    EXPLORE("Explore & Search Grid", "Discovery grid and suggested content")
}

@Serializable
enum class BlockMode(val label: String) {
    ALLOW("Allowed"),
    TIMED("Timed"),
    BLOCK("Blocked")
}

@Serializable
data class FeatureRule(
    val feature: AppFeature,
    val mode: BlockMode = BlockMode.TIMED,
    val sessionLimitMinutes: Int = 15,
    val dailyLimitMinutes: Int = 60,
    val allowSingleDmItem: Boolean = true
)

@Serializable
data class AppPolicy(
    val packageName: String = "com.instagram.android",
    val appDisplayName: String = "Instagram",
    val enabled: Boolean = true,
    val blockAllFeatures: Boolean = false,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val rules: Map<AppFeature, FeatureRule> = mapOf(
        AppFeature.HOME_AND_STORIES to FeatureRule(
            feature = AppFeature.HOME_AND_STORIES,
            mode = BlockMode.TIMED,
            sessionLimitMinutes = 10,
            dailyLimitMinutes = 30
        ),
        AppFeature.REELS to FeatureRule(
            feature = AppFeature.REELS,
            mode = BlockMode.BLOCK,
            sessionLimitMinutes = 5,
            dailyLimitMinutes = 15,
            allowSingleDmItem = true
        ),
        AppFeature.EXPLORE to FeatureRule(
            feature = AppFeature.EXPLORE,
            mode = BlockMode.BLOCK,
            sessionLimitMinutes = 5,
            dailyLimitMinutes = 15
        )
    ),
    val settingsLockedUntilEpochMs: Long = 0L,
    val strictLockMode: Boolean = false
)

@Serializable
data class FeatureUsageState(
    val dateIso: String = "",
    val dailyUsedSeconds: Long = 0L,
    val currentSessionUsedSeconds: Long = 0L,
    val lastActiveEpochMs: Long = 0L,
    val cooldownUntilEpochMs: Long = 0L
)

@Serializable
data class UsageSnapshot(
    val byFeature: Map<AppFeature, FeatureUsageState> = emptyMap()
)
