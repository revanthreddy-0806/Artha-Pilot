package com.arthapilot.app.ui.auth

import com.arthapilot.app.data.repository.AuthRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.AuthTokens
import com.arthapilot.app.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is unauthenticated`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun `successful login transitions to authenticated`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        viewModel.onEmailChange("ada@x.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
        assertTrue(viewModel.uiState.value.error == null)
        assertTrue(!viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed login shows user-friendly error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply {
            failLogin = ApiException.Server("Invalid email or password", "invalid_credentials", 401)
        }
        val viewModel = AuthViewModel(repo)
        viewModel.onEmailChange("ada@x.com")
        viewModel.onPasswordChange("wrong")
        viewModel.login()
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
        assertEquals("Invalid email or password.", viewModel.uiState.value.error)
    }

    @Test
    fun `network failure during login surfaces connection error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { failLogin = ApiException.Network() }
        val viewModel = AuthViewModel(repo)
        viewModel.onEmailChange("ada@x.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    @Test
    fun `successful registration transitions to authenticated`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        viewModel.onFullNameChange("Ada Lovelace")
        viewModel.onEmailChange("ada@x.com")
        viewModel.onPasswordChange("password123")
        viewModel.register()
        advanceUntilIdle()
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
    }

    @Test
    fun `logout returns to unauthenticated`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        viewModel.onEmailChange("ada@x.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()
        assertEquals(AuthState.Authenticated, viewModel.authState.value)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    private class FakeAuthRepository : AuthRepository {
        private val _tokens = MutableStateFlow<AuthTokens?>(null)
        override val tokens: Flow<AuthTokens?> = _tokens

        var failLogin: ApiException? = null

        override suspend fun register(fullName: String, email: String, password: String): User =
            authenticate()

        override suspend fun login(email: String, password: String): User = authenticate()

        private fun authenticate(): User {
            failLogin?.let { throw it }
            _tokens.value = AuthTokens(accessToken = "access", refreshToken = "refresh")
            return User(1, "ada@x.com", "Ada Lovelace", true, "2026-01-01T00:00:00Z")
        }

        override suspend fun me(): User =
            User(1, "ada@x.com", "Ada Lovelace", true, "2026-01-01T00:00:00Z")

        override suspend fun logout() {
            _tokens.value = null
        }
    }
}