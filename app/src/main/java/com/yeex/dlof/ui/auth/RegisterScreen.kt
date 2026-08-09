package com.yeex.dlof.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkSurfaceVariant
import com.yeex.dlof.ui.theme.YeexGray
import com.yeex.dlof.ui.theme.YeexWhite
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.UsernameValidator

private const val DISPLAY_NAME_MAX = 30

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onRegistered: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val context = LocalContext.current
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var confirmTouched by remember { mutableStateOf(false) }
    val validation = remember(identifier) { UsernameValidator.validate(identifier) }
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading

    val passwordsMatch = confirmPassword.isEmpty() || confirmPassword == password
    val canSubmit = validation.isValid && password.length >= 6 && confirmPassword == password && agreedToTerms && !isLoading

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onRegistered()
    }

    AuthBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthBrandHeader(tagline = stringResource(R.string.register_tagline))
            Spacer(Modifier.height(28.dp))

            AuthCard {
                Text(
                    stringResource(R.string.register_title),
                    color = YeexWhite,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))

                AuthIdentifierField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = stringResource(R.string.field_identifier),
                    isError = identifier.isNotEmpty() && !validation.isValid,
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                    enabled = !isLoading
                )
                IdentifierChecklist(identifier)

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= DISPLAY_NAME_MAX) displayName = it },
                    label = { Text(stringResource(R.string.field_display_name)) },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                    supportingText = {
                        Text(
                            "${displayName.length}/$DISPLAY_NAME_MAX",
                            color = YeexGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.field_password),
                    isError = false,
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                    enabled = !isLoading
                )
                PasswordStrengthMeter(password)

                Spacer(Modifier.height(12.dp))
                AuthPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; confirmTouched = true },
                    label = stringResource(R.string.field_confirm_password),
                    isError = confirmTouched && !passwordsMatch,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (canSubmit) {
                            viewModel.register(context, identifier, password, displayName, LocaleUtil.getSavedLanguage(context))
                        }
                    },
                    enabled = !isLoading
                )
                if (confirmTouched && !passwordsMatch) {
                    Spacer(Modifier.height(6.dp))
                    AuthErrorBanner(stringResource(R.string.error_password_mismatch))
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(YeexDarkSurfaceVariant)
                        .clickable(enabled = !isLoading) { agreedToTerms = !agreedToTerms }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (agreedToTerms) YeexAccent else Color(0xFF322D42)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (agreedToTerms) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = YeexWhite, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.terms_agreement, stringResource(R.string.app_name)),
                        color = YeexGray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state is AuthUiState.Error) {
                    Spacer(Modifier.height(12.dp))
                    AuthErrorBanner(stringResource(authErrorStringRes((state as AuthUiState.Error).key)))
                }

                Spacer(Modifier.height(20.dp))
                GradientAuthButton(
                    text = stringResource(R.string.btn_register),
                    enabled = canSubmit,
                    isLoading = isLoading,
                    onClick = { viewModel.register(context, identifier, password, displayName, LocaleUtil.getSavedLanguage(context)) }
                )
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onGoToLogin, enabled = !isLoading) {
                Text(stringResource(R.string.have_account), color = YeexAccent)
            }
        }
    }
}
