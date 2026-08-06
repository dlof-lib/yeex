package com.yeex.dlof.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R

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
    "google_cancelled" -> R.string.auth_error_google_cancelled
    "weak_password" -> R.string.auth_error_weak_password
    "network_error" -> R.string.auth_error_network
    else -> R.string.auth_error_unknown
}

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isLoading = state is AuthUiState.Loading

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onLoggedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it.lowercase() },
            label = { Text(stringResource(R.string.field_identifier)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.field_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.login(identifier, password) },
            enabled = identifier.isNotBlank() && password.isNotBlank() && state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_login))
        }

        if (state is AuthUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(authErrorStringRes((state as AuthUiState.Error).key)),
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.or_divider),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_continue_google))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { (context as? Activity)?.let { viewModel.signInWithGithub(it) } },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_continue_github))
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onGoToRegister) { Text(stringResource(R.string.no_account)) }
    }
}
