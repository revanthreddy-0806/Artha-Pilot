package com.arthapilot.app.data.repository

import com.arthapilot.app.data.local.TokenStore
import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.domain.model.AuthTokens
import com.arthapilot.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val tokens: Flow<AuthTokens?>
    suspend fun register(fullName: String, email: String, password: String): User
    suspend fun login(email: String, password: String): User
    suspend fun me(): User
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override val tokens: Flow<AuthTokens?> = tokenStore.tokens

    override suspend fun register(fullName: String, email: String, password: String): User {
        try {
            val response = api.register(
                RegisterRequestDto(fullName = fullName, email = email, password = password),
            )
            tokenStore.save(response.toDomain())
            return me()
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
    }

    override suspend fun login(email: String, password: String): User {
        try {
            val response = api.login(LoginRequestDto(email = email, password = password))
            tokenStore.save(response.toDomain())
            return me()
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
    }

    override suspend fun me(): User {
        try {
            return api.me().toDomain()
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
    }

    override suspend fun logout() {
        tokenStore.clear()
    }
}
