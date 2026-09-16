package com.example.mcpinspector.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "server_profiles")

/** Persists [ServerProfile]s (including their header values, e.g. bearer tokens) via DataStore. */
class ProfileStore(private val context: Context) {

    private val profilesKey = stringPreferencesKey("profiles_json")
    private val json = Json { ignoreUnknownKeys = true }

    val profiles: Flow<List<ServerProfile>> = context.dataStore.data.map { prefs ->
        decode(prefs[profilesKey])
    }

    suspend fun upsert(profile: ServerProfile) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[profilesKey]).filterNot { it.id == profile.id } + profile
            prefs[profilesKey] = json.encodeToString(updated)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[profilesKey]).filterNot { it.id == id }
            prefs[profilesKey] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<ServerProfile> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
    }
}
