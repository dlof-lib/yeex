package com.yeex.dlof.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

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
            Text("خطأ في الدخول", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onGoToRegister) { Text(stringResource(R.string.no_account)) }
    }
}
