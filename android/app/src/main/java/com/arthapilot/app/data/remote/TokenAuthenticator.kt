package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.TokenStore
import com.arthapilot.app.data.remote.dto.RefreshRequestDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

/**
 * OkHttp Authenticator: on a 401, rotates the refresh token against the
 * backend and retries the original request once with the fresh access token.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val apiBaseUrl: String,
) : Authenticator {

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    @Volatile
    private var refreshing = false

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenStore.blockingTokens()?.refreshToken ?: return null

        synchronized(this) {
            if (refreshing) return null
            refreshing = true
        }

        try {
            // Refresh once per request chain; a second 401 means the session is dead.
            if (response.request.header(REFRESH_ATTEMPT_HEADER) != null) {
                tokenStore.blockingClear()
                return null
            }

            val tokens = refreshTokens(refreshToken) ?: return null

            return response.request.newBuilder()
                .header(REFRESH_ATTEMPT_HEADER, "1")
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .build()
        } finally {
            refreshing = false
        }
    }

    private fun refreshTokens(refreshToken: String): TokenResponseDto? {
        val payload = gson.toJson(RefreshRequestDto(refreshToken))
        val request = Request.Builder()
            .url(apiBaseUrl.trimEnd('/') + "/" + ArthaApi.REFRESH_PATH)
            .header("Content-Type", "application/json")
            .header(REFRESH_ATTEMPT_HEADER, "1")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    tokenStore.blockingClear()
                    return null
                }
                val tokens = resp.body.string().let {
                    gson.fromJson(it, TokenResponseDto::class.java)
                }
                tokenStore.blockingSave(tokens.toDomain())
                tokens
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val REFRESH_ATTEMPT_HEADER = "X-Artha-Refresh-Attempt"
    }
}
