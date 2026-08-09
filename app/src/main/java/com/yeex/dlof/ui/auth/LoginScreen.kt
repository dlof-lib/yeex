package com.yeex.dlof.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent

/** Maps an [AuthRepository][com.yeex.dlof.data.repository.AuthRepository] error key to a localized, specific message. */
internal fun authErrorStringRes(key: String): Int = when (key) {
    "invalid_credentials" -> R.string.auth_error_invalid_credentials
    "identifier_taken" -> R.string.auth_error_identifier_taken
    "length" -> R.string.auth_error_length
    "forbidden_char" -> R.string.auth_error_forbidden_char
    "uppercase" -> R.string.auth_error_uppercase
    "dot_placement" -> R.string.auth_error_dot_placement
    "invalid_chars" -> R.string.auth_error_invalid_chars
    "profile_missing" -> R.string.auth_error_profile_missing
    "weak_password" -> R.string.auth_error_weak_password
    "network_error" -> R.string.auth_error_network
    else -> R.string.auth_error_unknown
}

/**
 * Identifier + password sign-in — this is the only sign-in method in the
 * app (no Google/GitHub buttons). When opened from the profile screen's
 * "إضافة حساب" (add account) action, [prefillIdentifier] pre-fills the
 * "معرف" field so adding a second known account only requires the password.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    prefillIdentifier: String = "",
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit
) {
    var identifier by remember { mutableStateOf(prefillIdentifier) }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isLoading = state is AuthUiState.Loading

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onLoggedIn()
    }

    AuthBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthBrandHeader(tagline = stringResource(R.string.login_tagline))
            Spacer(Modifier.height(32.dp))

            AuthCard {
                AuthIdentifierField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = stringResource(R.string.field_identifier),
                    isError = state is AuthUiState.Error,
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                    enabled = !isLoading
                )
                Spacer(Modifier.height(12.dp))
                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.field_password),
                    isError = state is AuthUiState.Error,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (identifier.isNotBlank() && password.isNotBlank()) viewModel.login(context, identifier, password)
                    },
                    enabled = !isLoading
                )

                if (state is AuthUiState.Error) {
                    Spacer(Modifier.height(12.dp))
                    AuthErrorBanner(stringResource(authErrorStringRes((state as AuthUiState.Error).key)))
                }

                Spacer(Modifier.height(20.dp))
                GradientAuthButton(
                    text = stringResource(R.string.btn_login),
                    enabled = identifier.isNotBlank() && password.isNotBlank(),
                    isLoading = isLoading,
                    onClick = { viewModel.login(context, identifier, password) }
                )
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onGoToRegister, enabled = !isLoading) {
                Text(stringResource(R.string.no_account), color = YeexAccent)
            }
        }
    }
}
