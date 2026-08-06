package com.arthapilot.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.arthapilot.app.data.local.TokenStore
import com.arthapilot.app.domain.model.AuthTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TokenAuthenticatorTest {

    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
    }

    private fun newServer(): MockWebServer {
        val server = MockWebServer()
        server.start()
        servers.add(server)
        return server
    }

    /** A deterministic, filesystem-free backing store for tests. */
    private class InMemoryDataStore(
        initial: Map<String, String> = emptyMap(),
    ) : DataStore<Preferences> {
        private val prefs = MutableStateFlow(
            initial.entries.fold(emptyPreferences()) { acc, (key, value) ->
                acc.toMutablePreferences().apply { this[androidx.datastore.preferences.core.stringPreferencesKey(key)] = value }
            },
        )

        override val data: Flow<Preferences> = prefs.asStateFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val current = prefs.value
            val updated = transform(current)
            prefs.value = updated
            return updated
        }
    }

    private fun seededStore(access: String, refresh: String) = TokenStore(
        InMemoryDataStore(
            mapOf(
                "access_token" to access,
                "refresh_token" to refresh,
            ),
        ),
    )

    private fun challenge(code: Int): Response {
        val request = Request.Builder().url("http://localhost/").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("mock")
            .body("".toResponseBody())
            .build()
    }

    @Test
    fun `refresh rejected with 401 clears tokens and drops request`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"code":"invalid_refresh_token","message":"Invalid"}}"""),
        )
        val store = seededStore("access-1", "refresh-1")
        val authenticator = TokenAuthenticator(store, server.url("/api/v1/").toString())

        val result = authenticator.authenticate(null, challenge(401))

        assertNull(result)
        assertNull(store.blockingTokens())
        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", recorded.path)
    }

    @Test
    fun `refresh 5xx keeps tokens and drops request`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":{"code":"internal_error","message":"boom"}}"""),
        )
        val store = seededStore("access-1", "refresh-1")
        val authenticator = TokenAuthenticator(store, server.url("/api/v1/").toString())

        val result = authenticator.authenticate(null, challenge(401))

        assertNull(result)
        val tokens = store.blockingTokens()
        assertNotNull(tokens)
        assertEquals("access-1", tokens!!.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun `refresh 429 keeps tokens and drops request`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"code":"rate_limited","message":"slow down"}}"""),
        )
        val store = seededStore("access-1", "refresh-1")
        val authenticator = TokenAuthenticator(store, server.url("/api/v1/").toString())

        val result = authenticator.authenticate(null, challenge(401))

        assertNull(result)
        assertNotNull(store.blockingTokens())
    }

    @Test
    fun `successful refresh retries request with fresh token`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"access_token":"access-2","refresh_token":"refresh-2","token_type":"bearer"}""",
                ),
        )
        val store = seededStore("access-1", "refresh-1")
        val authenticator = TokenAuthenticator(store, server.url("/api/v1/").toString())

        val result = authenticator.authenticate(null, challenge(401))

        assertNotNull(result)
        assertEquals("Bearer access-2", result!!.header("Authorization"))
        assertEquals("access-2", store.blockingTokens()!!.accessToken)
        assertEquals("refresh-2", store.blockingTokens()!!.refreshToken)
    }
}