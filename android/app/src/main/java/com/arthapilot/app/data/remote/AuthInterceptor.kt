package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the current access token to every authenticated request. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val tokens = tokenStore.blockingTokens()
        val request = if (tokens == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .build()
        }
        return chain.proceed(request)
    }
}
