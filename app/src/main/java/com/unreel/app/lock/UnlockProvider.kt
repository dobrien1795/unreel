package com.unreel.app.lock

import com.unreel.app.data.AppPolicy
import com.unreel.app.data.BlockMode
import com.unreel.app.data.FeatureRule

interface UnlockProvider {
    fun canModifyPolicy(current: AppPolicy, proposed: AppPolicy, nowMs: Long = System.currentTimeMillis()): Boolean
    fun lockReason(current: AppPolicy, nowMs: Long = System.currentTimeMillis()): String?
}

class StrictTimeLockProvider : UnlockProvider {

    override fun canModifyPolicy(current: AppPolicy, proposed: AppPolicy, nowMs: Long): Boolean {
        if (!current.strictLockMode) return true
        if (isMakingStricter(current, proposed)) return true
        return nowMs >= current.settingsLockedUntilEpochMs
    }

    override fun lockReason(current: AppPolicy, nowMs: Long): String? {
        if (!current.strictLockMode || nowMs >= current.settingsLockedUntilEpochMs) return null
        val mins = ((current.settingsLockedUntilEpochMs - nowMs) / 60_000L) + 1
        return "Settings locked for ${mins}m."
    }

    private fun isMakingStricter(current: AppPolicy, proposed: AppPolicy): Boolean {
        if (!current.enabled && proposed.enabled) return true
        if (!current.blockAllFeatures && proposed.blockAllFeatures) return true

        for ((feature, oldRule) in current.rules) {
            val newRule = proposed.rules[feature] ?: continue
            if (strictnessRank(newRule) < strictnessRank(oldRule)) return false
            if (newRule.sessionLimitMinutes > oldRule.sessionLimitMinutes) return false
            if (newRule.dailyLimitMinutes > oldRule.dailyLimitMinutes) return false
        }
        return true
    }

    private fun strictnessRank(rule: FeatureRule): Int = when (rule.mode) {
        BlockMode.ALLOW -> 0
        BlockMode.TIMED -> 1
        BlockMode.BLOCK -> 2
    }
}
