package com.arthapilot.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arthapilot.app.domain.model.AuthTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** Persists the JWT pair (access + refresh) in DataStore preferences. */
class TokenStore(private val dataStore: DataStore<Preferences>) {

    val tokens: Flow<AuthTokens?> = dataStore.data.map { prefs ->
        val access = prefs[KEY_ACCESS] ?: return@map null
        val refresh = prefs[KEY_REFRESH] ?: return@map null
        AuthTokens(accessToken = access, refreshToken = refresh)
    }

    suspend fun save(tokens: AuthTokens) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS] = tokens.accessToken
            prefs[KEY_REFRESH] = tokens.refreshToken
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Blocking variants for use inside OkHttp interceptors/authenticators. */
    fun blockingTokens(): AuthTokens? = runBlocking { tokens.first() }

    fun blockingSave(tokens: AuthTokens) = runBlocking { save(tokens) }

    fun blockingClear() = runBlocking { clear() }

    private companion object {
        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
    }
}
