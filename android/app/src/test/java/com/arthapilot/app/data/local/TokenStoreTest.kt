package com.arthapilot.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arthapilot.app.domain.model.AuthTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenStoreTest {

    private fun inMemoryDataStore() = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            var result: Preferences = emptyPreferences()
            state.update { current ->
                result = transform(current)
                result
            }
            return result
        }
    }

    @Test
    fun `save persists tokens and clear removes them`() = runTest {
        val store = TokenStore(inMemoryDataStore())

        store.save(AuthTokens(accessToken = "access-1", refreshToken = "refresh-1"))
        assertEquals("access-1", store.tokens.first()?.accessToken)
        assertEquals("refresh-1", store.tokens.first()?.refreshToken)

        store.clear()
        assertNull(store.tokens.first())
    }

    @Test
    fun `clear preserves other preferences in the same DataStore`() = runTest {
        val dataStore = inMemoryDataStore()
        val store = TokenStore(dataStore)

        // The SQLCipher passphrase lives in the same DataStore file.
        dataStore.edit { it[KEY_PASSPHRASE] = "encrypted-blob" }
        store.save(AuthTokens(accessToken = "access-1", refreshToken = "refresh-1"))

        store.clear()

        assertNull(store.tokens.first())
        assertEquals("encrypted-blob", dataStore.data.first()[KEY_PASSPHRASE])
    }

    private companion object {
        val KEY_PASSPHRASE = stringPreferencesKey("db_passphrase_encrypted")
    }
}