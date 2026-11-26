package com.unreel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unreel_prefs")

class PolicyRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val KEY_POLICIES = stringPreferencesKey("app_policies_json")
    private val KEY_USAGE = stringPreferencesKey("usage_snapshot_json")

    val policiesFlow: Flow<Map<String, AppPolicy>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_POLICIES]
        if (raw.isNullOrBlank()) {
            mapOf("com.instagram.android" to AppPolicy())
        } else {
            runCatching { json.decodeFromString<Map<String, AppPolicy>>(raw) }
                .getOrDefault(mapOf("com.instagram.android" to AppPolicy()))
        }
    }

    val usageFlow: Flow<UsageSnapshot> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_USAGE]
        if (raw.isNullOrBlank()) {
            UsageSnapshot()
        } else {
            runCatching { json.decodeFromString<UsageSnapshot>(raw) }
                .getOrDefault(UsageSnapshot())
        }
    }

    suspend fun getPolicy(packageName: String): AppPolicy {
        return policiesFlow.first()[packageName] ?: AppPolicy(packageName = packageName)
    }

    suspend fun savePolicy(policy: AppPolicy) {
        context.dataStore.edit { prefs ->
            val current = policiesFlow.first().toMutableMap()
            current[policy.packageName] = policy
            prefs[KEY_POLICIES] = json.encodeToString(current)
        }
    }

    suspend fun getUsageSnapshot(): UsageSnapshot = usageFlow.first()

    suspend fun saveUsageSnapshot(snapshot: UsageSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USAGE] = json.encodeToString(snapshot)
        }
    }
}
