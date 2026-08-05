package com.arthapilot.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.AuthRepository
import com.arthapilot.app.domain.model.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticated : AuthState
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.tokens
        .map { if (it == null) AuthState.Unauthenticated else AuthState.Authenticated }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unauthenticated)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun login() = submit { authRepository.login(_uiState.value.email, _uiState.value.password) }

    fun register() = submit {
        authRepository.register(
            fullName = _uiState.value.fullName,
            email = _uiState.value.email,
            password = _uiState.value.password,
        )
    }

    fun logout() = viewModelScope.launch {
        try {
            authRepository.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: the token store clear is idempotent and can be retried on the next logout.
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
