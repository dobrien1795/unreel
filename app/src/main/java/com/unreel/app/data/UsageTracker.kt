package com.unreel.app.data

import java.time.LocalDate
import java.time.ZoneId

sealed class EvaluationResult {
    data object Allowed : EvaluationResult()
    data class TimedActive(
        val sessionRemainingSeconds: Long,
        val sessionsRemainingToday: Int,
        val totalSessionsAllowed: Int,
        val warningLevel: WarningLevel
    ) : EvaluationResult()
    data class Blocked(
        val reason: String,
        val sessionRemainingSeconds: Long = 0L,
        val cooldownRemainingSeconds: Long = 0L,
        val timeExpired: Boolean = false
    ) : EvaluationResult()
}

enum class WarningLevel {
    NONE,
    TWO_MINUTES,
    THIRTY_SECONDS
}

class UsageTracker(private val repository: PolicyRepository) {

    companion object {
        private const val SESSION_IDLE_RESET_MS = 2 * 60 * 1000L
    }

    suspend fun tickAndEvaluate(
        packageName: String,
        feature: AppFeature,
        elapsedSeconds: Long,
        nowMs: Long = System.currentTimeMillis()
    ): EvaluationResult {
        if (packageName != "com.instagram.android") return EvaluationResult.Allowed

        val policy = repository.getPolicy(packageName)
        if (!policy.enabled) return EvaluationResult.Allowed

        val today = LocalDate.now(ZoneId.systemDefault())
        if (!policy.activeDays.contains(today.dayOfWeek)) {
            return EvaluationResult.Allowed
        }

        if (policy.blockAllFeatures) {
            return EvaluationResult.Blocked(
                reason = "All features are set to blocked in Unreel.",
                timeExpired = false
            )
        }

        val rule = policy.rules[feature] ?: return EvaluationResult.Allowed

        return when (rule.mode) {
            BlockMode.ALLOW -> EvaluationResult.Allowed
            BlockMode.BLOCK -> EvaluationResult.Blocked(
                reason = "${feature.displayName} are blocked in Unreel.",
                timeExpired = false
            )
            BlockMode.TIMED -> evaluateTimedRule(feature, rule, elapsedSeconds, today.toString(), nowMs)
        }
    }

    private suspend fun evaluateTimedRule(
        feature: AppFeature,
        rule: FeatureRule,
        elapsedSeconds: Long,
        todayIso: String,
        nowMs: Long
    ): EvaluationResult {
        val snapshot = repository.getUsageSnapshot()
        val prev = snapshot.byFeature[feature] ?: FeatureUsageState(dateIso = todayIso)

        var state = if (prev.dateIso != todayIso) {
            FeatureUsageState(dateIso = todayIso)
        } else {
            prev
        }

        val maxUses = rule.dailyUsesAllowed.coerceAtLeast(1)
        val sessionCapSeconds = (rule.sessionLimitMinutes.coerceAtLeast(1)) * 60L
        val cooldownMs = (rule.cooldownMinutes.coerceAtLeast(1)) * 60_000L

        if (state.cooldownUntilEpochMs > nowMs) {
            val cdRemainingSecs = ((state.cooldownUntilEpochMs - nowMs) / 1000L).coerceAtLeast(1L)
            val minsLeft = (cdRemainingSecs / 60L) + 1
            return EvaluationResult.Blocked(
                reason = "Your time has run out — wait ${minsLeft}m before going back to ${feature.displayName}.",
                sessionRemainingSeconds = 0L,
                cooldownRemainingSeconds = cdRemainingSecs,
                timeExpired = true
            )
        }

        if (state.cooldownUntilEpochMs in 1L..nowMs) {
            state = state.copy(
                cooldownUntilEpochMs = 0L,
                currentSessionUsedSeconds = 0L
            )
        }

        if (state.sessionsUsedToday >= maxUses) {
            return EvaluationResult.Blocked(
                reason = "Your time has run out — all $maxUses daily sessions used for ${feature.displayName}.",
                sessionRemainingSeconds = 0L,
                cooldownRemainingSeconds = 0L,
                timeExpired = true
            )
        }

        if (state.lastActiveEpochMs > 0L && (nowMs - state.lastActiveEpochMs) > SESSION_IDLE_RESET_MS) {
            state = state.copy(currentSessionUsedSeconds = 0L)
        }

        val safeElapsed = elapsedSeconds.coerceAtLeast(0L)
        val updatedDaily = state.dailyUsedSeconds + safeElapsed
        val updatedSession = state.currentSessionUsedSeconds + safeElapsed

        if (updatedSession >= sessionCapSeconds) {
            val completedSessions = (state.sessionsUsedToday + 1).coerceAtMost(maxUses)
            val hasMoreSessions = completedSessions < maxUses
            val nextCooldownUntil = if (hasMoreSessions) nowMs + cooldownMs else 0L

            val updatedState = state.copy(
                dailyUsedSeconds = updatedDaily,
                currentSessionUsedSeconds = 0L,
                sessionsUsedToday = completedSessions,
                lastActiveEpochMs = nowMs,
                cooldownUntilEpochMs = nextCooldownUntil
            )
            if (safeElapsed > 0L) {
                persistFeatureState(snapshot, feature, updatedState)
            }

            val cdSecs = if (hasMoreSessions) cooldownMs / 1000L else 0L
            val reason = if (hasMoreSessions) {
                "Your ${rule.sessionLimitMinutes}m session is up — wait ${rule.cooldownMinutes}m to go back."
            } else {
                "Your time has run out — all $maxUses sessions completed for ${feature.displayName}."
            }

            return EvaluationResult.Blocked(
                reason = reason,
                sessionRemainingSeconds = 0L,
                cooldownRemainingSeconds = cdSecs,
                timeExpired = true
            )
        }

        val nextState = state.copy(
            dailyUsedSeconds = updatedDaily,
            currentSessionUsedSeconds = updatedSession,
            lastActiveEpochMs = nowMs
        )
        if (safeElapsed > 0L) {
            persistFeatureState(snapshot, feature, nextState)
        }

        val remainingSession = (sessionCapSeconds - updatedSession).coerceAtLeast(0L)
        val remainingUses = (maxUses - state.sessionsUsedToday).coerceAtLeast(0)

        val warning = when {
            remainingSession <= 30L -> WarningLevel.THIRTY_SECONDS
            remainingSession <= 120L -> WarningLevel.TWO_MINUTES
            else -> WarningLevel.NONE
        }

        return EvaluationResult.TimedActive(
            sessionRemainingSeconds = remainingSession,
            sessionsRemainingToday = remainingUses,
            totalSessionsAllowed = maxUses,
            warningLevel = warning
        )
    }

    private suspend fun persistFeatureState(
        snapshot: UsageSnapshot,
        feature: AppFeature,
        newState: FeatureUsageState
    ) {
        val updatedMap = snapshot.byFeature.toMutableMap()
        updatedMap[feature] = newState
        repository.saveUsageSnapshot(snapshot.copy(byFeature = updatedMap))
    }
}
