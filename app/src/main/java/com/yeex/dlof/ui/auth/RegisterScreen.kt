package com.yeex.dlof.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.util.UsernameValidator
import androidx.compose.ui.res.stringResource

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onRegistered: () -> Unit,
    onGoToLogin: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val validation = remember(identifier) { UsernameValidator.validate(identifier) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onRegistered()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it.lowercase() },
            label = { Text(stringResource(R.string.field_identifier)) },
            isError = identifier.isNotEmpty() && !validation.isValid,
            supportingText = { Text(stringResource(R.string.identifier_rules)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.field_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.field_password)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.register(identifier, password, displayName, "ar") },
            enabled = validation.isValid && password.length >= 6 && state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_register))
        }

        if (state is AuthUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text("خطأ: ${(state as AuthUiState.Error).key}", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onGoToLogin) { Text(stringResource(R.string.have_account)) }
    }
}
