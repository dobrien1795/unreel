package com.unreel.app.data

import java.time.LocalDate
import java.time.ZoneId

sealed class EvaluationResult {
    data object Allowed : EvaluationResult()
    data class TimedActive(
        val remainingSeconds: Long,
        val warningLevel: WarningLevel
    ) : EvaluationResult()
    data class Blocked(val reason: String) : EvaluationResult()
}

enum class WarningLevel {
    NONE,
    TWO_MINUTES,
    THIRTY_SECONDS
}

class UsageTracker(private val repository: PolicyRepository) {

    companion object {
        private const val SESSION_IDLE_RESET_MS = 5 * 60 * 1000L
        private const val SESSION_COOLDOWN_MS = 15 * 60 * 1000L
    }

    suspend fun tickAndEvaluate(
        packageName: String,
        feature: AppFeature,
        elapsedSeconds: Long,
        nowMs: Long = System.currentTimeMillis()
    ): EvaluationResult {
        val policy = repository.getPolicy(packageName)
        if (!policy.enabled) return EvaluationResult.Allowed

        val today = LocalDate.now(ZoneId.systemDefault())
        if (!policy.activeDays.contains(today.dayOfWeek)) {
            return EvaluationResult.Allowed
        }

        if (policy.blockAllFeatures) {
            return EvaluationResult.Blocked("All Instagram features are currently locked.")
        }

        val rule = policy.rules[feature] ?: return EvaluationResult.Allowed

        return when (rule.mode) {
            BlockMode.ALLOW -> EvaluationResult.Allowed
            BlockMode.BLOCK -> EvaluationResult.Blocked("${feature.displayName} is blocked by your schedule.")
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

        if (state.cooldownUntilEpochMs > nowMs) {
            val minsLeft = ((state.cooldownUntilEpochMs - nowMs) / 60_000L) + 1
            return EvaluationResult.Blocked(
                "${feature.displayName} session limit reached. Cooldown: ${minsLeft}m left."
            )
        }

        if (state.lastActiveEpochMs > 0L && (nowMs - state.lastActiveEpochMs) > SESSION_IDLE_RESET_MS) {
            state = state.copy(currentSessionUsedSeconds = 0L)
        }

        val updatedDaily = state.dailyUsedSeconds + elapsedSeconds
        val updatedSession = state.currentSessionUsedSeconds + elapsedSeconds

        val dailyCapSeconds = rule.dailyLimitMinutes * 60L
        val sessionCapSeconds = rule.sessionLimitMinutes * 60L

        if (updatedDaily >= dailyCapSeconds) {
            val lockedState = state.copy(
                dailyUsedSeconds = dailyCapSeconds,
                currentSessionUsedSeconds = updatedSession,
                lastActiveEpochMs = nowMs
            )
            persistFeatureState(snapshot, feature, lockedState)
            return EvaluationResult.Blocked(
                "Daily limit (${rule.dailyLimitMinutes}m) reached for ${feature.displayName}."
            )
        }

        if (updatedSession >= sessionCapSeconds) {
            val cooldownState = state.copy(
                dailyUsedSeconds = updatedDaily,
                currentSessionUsedSeconds = 0L,
                lastActiveEpochMs = nowMs,
                cooldownUntilEpochMs = nowMs + SESSION_COOLDOWN_MS
            )
            persistFeatureState(snapshot, feature, cooldownState)
            return EvaluationResult.Blocked(
                "${rule.sessionLimitMinutes}m session limit reached for ${feature.displayName}. Take a 15m break."
            )
        }

        val nextState = state.copy(
            dailyUsedSeconds = updatedDaily,
            currentSessionUsedSeconds = updatedSession,
            lastActiveEpochMs = nowMs
        )
        persistFeatureState(snapshot, feature, nextState)

        val remainingSession = sessionCapSeconds - updatedSession
        val remainingDaily = dailyCapSeconds - updatedDaily
        val minRemaining = minOf(remainingSession, remainingDaily)

        val warning = when {
            minRemaining <= 30L -> WarningLevel.THIRTY_SECONDS
            minRemaining <= 120L -> WarningLevel.TWO_MINUTES
            else -> WarningLevel.NONE
        }

        return EvaluationResult.TimedActive(
            remainingSeconds = minRemaining,
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
