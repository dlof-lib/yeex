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
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            when (val result = repo.register(identifier, password, displayName, language)) {
                is AuthRepository.AuthResult.Success -> _state.value = AuthUiState.Success
                is AuthRepository.AuthResult.Failure -> _state.value = AuthUiState.Error(result.messageKey)
            }
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            when (val result = repo.login(identifier, password)) {
                is AuthRepository.AuthResult.Success -> _state.value = AuthUiState.Success
                is AuthRepository.AuthResult.Failure -> _state.value = AuthUiState.Error(result.messageKey)
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            when (val result = repo.signInWithGoogle(context)) {
                is AuthRepository.AuthResult.Success -> _state.value = AuthUiState.Success
                is AuthRepository.AuthResult.Failure -> _state.value = AuthUiState.Error(result.messageKey)
            }
        }
    }

    fun signInWithGithub(activity: Activity) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            when (val result = repo.signInWithGithub(activity)) {
                is AuthRepository.AuthResult.Success -> _state.value = AuthUiState.Success
                is AuthRepository.AuthResult.Failure -> _state.value = AuthUiState.Error(result.messageKey)
            }
        }
    }
}
