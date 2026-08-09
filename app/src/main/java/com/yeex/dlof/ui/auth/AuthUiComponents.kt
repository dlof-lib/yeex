package com.yeex.dlof.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexBlack
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexDarkSurface
import com.yeex.dlof.ui.theme.YeexDarkSurfaceVariant
import com.yeex.dlof.ui.theme.YeexGray
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.ui.theme.YeexWhite
import com.yeex.dlof.ui.theme.yeexBrandGradient

/**
 * Shared dark brand backdrop for the auth flow: base black with two softly
 * tinted violet/pink glows so Login/Register don't sit on a flat color.
 */
@Composable
fun AuthBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YeexBlack)
    ) {
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-90).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(YeexAccent.copy(alpha = 0.28f), Color.Transparent)))
        )
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 80.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(YeexPink.copy(alpha = 0.22f), Color.Transparent)))
        )
        content()
    }
}

/** The "y" wordmark + app name + a short tagline, shared by Login and Register. */
@Composable
fun AuthBrandHeader(tagline: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(yeexBrandGradient()),
            contentAlignment = Alignment.Center
        ) {
            Text("y", color = YeexWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.app_name),
            color = YeexWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(tagline, color = YeexGray, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** The rounded dark card the form fields sit inside on both screens. */
@Composable
fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(YeexDarkCard)
            .padding(20.dp),
        content = content
    )
}

private val authFieldColors: androidx.compose.material3.TextFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = YeexDarkSurfaceVariant,
        unfocusedContainerColor = YeexDarkSurface,
        disabledContainerColor = YeexDarkSurface,
        focusedBorderColor = YeexAccent,
        unfocusedBorderColor = Color(0xFF322D42),
        focusedTextColor = YeexWhite,
        unfocusedTextColor = YeexWhite,
        cursorColor = YeexAccent,
        focusedLabelColor = YeexAccent,
        unfocusedLabelColor = YeexGray,
        focusedLeadingIconColor = YeexAccent,
        unfocusedLeadingIconColor = YeexGray,
        focusedTrailingIconColor = YeexAccent,
        unfocusedTrailingIconColor = YeexGray,
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorLeadingIconColor = MaterialTheme.colorScheme.error
    )

/** The "معرف" field: person icon, no visual transformation, lowercase-only input. */
@Composable
fun AuthIdentifierField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.lowercase()) },
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A password field with a lock icon and a show/hide eye toggle. */
@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    enabled: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.hide_password else R.string.show_password
                    )
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = isError,
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Primary call-to-action button filled with the brand purple → pink gradient. */
@Composable
fun GradientAuthButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "authButtonAlpha")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(yeexBrandGradient())
            .alpha(alpha)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = YeexWhite, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, color = YeexWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/** Inline error banner used instead of a bare line of red text. */
@Composable
fun AuthErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Live checklist for the identifier rules (length / allowed chars / no
 * uppercase / dot placement) — replaces the old static "rules" caption with
 * per-rule check/cross feedback that updates as the person types.
 */
@Composable
fun IdentifierChecklist(identifier: String) {
    val lengthOk = identifier.length in com.yeex.dlof.util.UsernameValidator.MIN_LENGTH..com.yeex.dlof.util.UsernameValidator.MAX_LENGTH
    val noForbidden = identifier.none { it in setOf('_', '-', '~', ' ') }
    val noUppercase = identifier.none { it.isUpperCase() }
    val dotsOk = !identifier.startsWith(".") && !identifier.endsWith(".") && !identifier.contains("..")

    AnimatedVisibility(visible = identifier.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
            ChecklistRow(stringResource(R.string.rule_length, com.yeex.dlof.util.UsernameValidator.MIN_LENGTH, com.yeex.dlof.util.UsernameValidator.MAX_LENGTH), lengthOk)
            ChecklistRow(stringResource(R.string.rule_lowercase), noUppercase)
            ChecklistRow(stringResource(R.string.rule_forbidden_chars), noForbidden)
            ChecklistRow(stringResource(R.string.rule_dots), dotsOk)
        }
    }
}

@Composable
private fun ChecklistRow(label: String, satisfied: Boolean) {
    val color by animateColorAsState(if (satisfied) Color(0xFF4CD787) else YeexGray, label = "ruleColor")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (satisfied) Color(0xFF4CD787).copy(alpha = 0.18f) else YeexDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (satisfied) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF4CD787), modifier = Modifier.size(10.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private enum class PasswordStrength(val labelRes: Int, val color: Color, val fraction: Float) {
    WEAK(R.string.password_strength_weak, Color(0xFFFF5470), 0.34f),
    MEDIUM(R.string.password_strength_medium, Color(0xFFFFB020), 0.67f),
    STRONG(R.string.password_strength_strong, Color(0xFF4CD787), 1f)
}

private fun scorePassword(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 6) score++
    if (password.length >= 10) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 3 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }
}

/** Colored strength bar + label shown under the password field while registering. */
@Composable
fun PasswordStrengthMeter(password: String) {
    AnimatedVisibility(visible = password.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val strength = remember(password) { scorePassword(password) }
        val fraction by animateFloatAsState(strength.fraction, label = "strengthFraction")
        Column(Modifier.padding(top = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(YeexDarkSurfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(strength.color)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(strength.labelRes), color = strength.color, style = MaterialTheme.typography.labelSmall)
        }
    }
}
