package com.unreel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unreel.app.data.AppFeature
import com.unreel.app.data.AppPolicy
import com.unreel.app.data.BlockMode
import com.unreel.app.data.FeatureRule
import com.unreel.app.data.PolicyRepository
import com.unreel.app.data.UsageSnapshot
import com.unreel.app.lock.StrictTimeLockProvider
import com.unreel.app.lock.UnlockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PolicyRepository(application)
    private val unlockProvider: UnlockProvider = StrictTimeLockProvider()

    val instagramPolicy: StateFlow<AppPolicy> = repository.policiesFlow
        .map { it["com.instagram.android"] ?: AppPolicy() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPolicy())

    val usageSnapshot: StateFlow<UsageSnapshot> = repository.usageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageSnapshot())

    private val _lockBannerMessage = MutableStateFlow<String?>(null)
    val lockBannerMessage: StateFlow<String?> = _lockBannerMessage.asStateFlow()

    fun toggleMasterEnabled(enabled: Boolean) {
        updatePolicy { it.copy(enabled = enabled) }
    }

    fun toggleBlockAll(blockAll: Boolean) {
        updatePolicy { it.copy(blockAllFeatures = blockAll) }
    }

    fun toggleDay(day: DayOfWeek) {
        updatePolicy { policy ->
            val updated = policy.activeDays.toMutableSet()
            if (updated.contains(day)) updated.remove(day) else updated.add(day)
            policy.copy(activeDays = updated)
        }
    }

    fun updateFeatureMode(feature: AppFeature, mode: BlockMode) {
        updatePolicy { policy ->
            val currentRule = policy.rules[feature] ?: FeatureRule(feature)
            val updatedRules = policy.rules.toMutableMap()
            updatedRules[feature] = currentRule.copy(mode = mode)
            policy.copy(rules = updatedRules)
        }
    }

    fun updateFeatureTimers(feature: AppFeature, sessionMins: Int, dailyMins: Int) {
        updatePolicy { policy ->
            val currentRule = policy.rules[feature] ?: FeatureRule(feature)
            val updatedRules = policy.rules.toMutableMap()
            updatedRules[feature] = currentRule.copy(
                sessionLimitMinutes = sessionMins.coerceIn(1, 120),
                dailyLimitMinutes = dailyMins.coerceIn(sessionMins, 480)
            )
            policy.copy(rules = updatedRules)
        }
    }

    fun toggleLockMode() {
        viewModelScope.launch {
            val current = instagramPolicy.value
            val now = System.currentTimeMillis()
            val isCurrentlyLocked = current.strictLockMode && now < current.settingsLockedUntilEpochMs
            if (isCurrentlyLocked) {
                repository.savePolicy(
                    current.copy(
                        strictLockMode = false,
                        settingsLockedUntilEpochMs = 0L
                    )
                )
                _lockBannerMessage.value = "Lock unlocked for testing."
            } else {
                val lockUntil = now + (12 * 3_600_000L)
                repository.savePolicy(
                    current.copy(
                        strictLockMode = true,
                        settingsLockedUntilEpochMs = lockUntil
                    )
                )
                _lockBannerMessage.value = "Settings locked for 12h."
            }
        }
    }

    fun clearMessage() {
        _lockBannerMessage.value = null
    }

    private fun updatePolicy(transform: (AppPolicy) -> AppPolicy) {
        viewModelScope.launch {
            val current = instagramPolicy.value
            val proposed = transform(current)
            if (!unlockProvider.canModifyPolicy(current, proposed)) {
                _lockBannerMessage.value = unlockProvider.lockReason(current)
                    ?: "Locked: cannot weaken restrictions right now."
                return@launch
            }
            repository.savePolicy(proposed)
        }
    }
}
