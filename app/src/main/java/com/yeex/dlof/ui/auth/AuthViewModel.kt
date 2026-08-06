package com.yeex.dlof.ui.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeex.dlof.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val key: String) : AuthUiState()
}

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state

    fun register(identifier: String, password: String, displayName: String, language: String) {
        runAuthAction { repo.register(identifier, password, displayName, language) }
    }

    fun login(identifier: String, password: String) {
        runAuthAction { repo.login(identifier, password) }
    }

    fun signInWithGoogle(context: Context) {
        runAuthAction { repo.signInWithGoogle(context) }
    }

    fun signInWithGithub(activity: Activity) {
        runAuthAction { repo.signInWithGithub(activity) }
    }

    /**
     * Runs an auth suspend call and always lands on Success/Error — never lets an
     * unexpected exception escape viewModelScope.launch uncaught, which would otherwise
     * crash the whole app instead of showing an in-UI error message.
     */
    private fun runAuthAction(action: suspend () -> AuthRepository.AuthResult) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            _state.value = try {
                when (val result = action()) {
                    is AuthRepository.AuthResult.Success -> AuthUiState.Success
                    is AuthRepository.AuthResult.Failure -> AuthUiState.Error(result.messageKey)
                }
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "unknown")
            }
        }
    }
}
