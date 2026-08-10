package com.arthapilot.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.arthapilot.app.data.local.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkModuleTest {

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
    fun `api client read timeout tolerates slow grounded LLM responses`() {
        val client = NetworkModule.provideOkHttpClient(TokenStore(inMemoryDataStore()))
        assertEquals(90, client.readTimeoutMillis / TimeUnit.SECONDS.toMillis(1).toInt())
    }
}