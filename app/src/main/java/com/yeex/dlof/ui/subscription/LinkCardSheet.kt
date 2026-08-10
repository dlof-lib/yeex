package com.yeex.dlof.ui.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yeex.dlof.R
import com.yeex.dlof.data.repository.SubscriptionRepository
import com.yeex.dlof.ui.theme.YeexAccent
import kotlinx.coroutines.launch

/** Recognizes the card network from its leading digit(s) — display only, no real BIN validation. */
private fun detectBrand(number: String): String = when {
    number.startsWith("4") -> "Visa"
    number.startsWith("5") -> "Mastercard"
    number.startsWith("34") || number.startsWith("37") -> "Amex"
    number.startsWith("2") || number.startsWith("50") || number.startsWith("58") || number.startsWith("60") -> "Mada"
    else -> "Card"
}

/**
 * Card-linking form. Per [com.yeex.dlof.data.model.PaymentCard]'s doc
 * comment, only a masked summary (brand/last4/expiry/holder name) is ever
 * sent to [SubscriptionRepository.linkCard] — the full number/CVV the
 * person types here are used purely to derive that summary on-device and
 * are never written anywhere.
 */
@Composable
fun LinkCardSheet(
    visible: Boolean,
    uid: String,
    repo: SubscriptionRepository = SubscriptionRepository(),
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    if (!visible) return
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.subscription_card_invalid)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = YeexAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.subscription_link_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { if (it.length <= 19) number = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.subscription_card_number_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { if (it.length <= 5) expiry = it },
                        label = { Text(stringResource(R.string.subscription_card_expiry_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = { if (it.length <= 4) cvv = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.subscription_card_cvv_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = holder,
                    onValueChange = { holder = it },
                    label = { Text(stringResource(R.string.subscription_card_holder_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.subscription_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !saving,
                        onClick = {
                            val expiryParts = expiry.split("/")
                            val month = expiryParts.getOrNull(0)?.trim()?.toIntOrNull()
                            val year = expiryParts.getOrNull(1)?.trim()?.toIntOrNull()
                            val last4 = number.takeLast(4)
                            if (number.length < 12 || month == null || month !in 1..12 || year == null || cvv.length < 3 || holder.isBlank()) {
                                error = invalidMessage
                                return@Button
                            }
                            error = null
                            saving = true
                            scope.launch {
                                runCatching {
                                    repo.linkCard(uid, detectBrand(number), last4, month, 2000 + (year % 100), holder)
                                }.onSuccess {
                                    saving = false
                                    onLinked()
                                }.onFailure {
                                    saving = false
                                    error = invalidMessage
                                }
                            }
                        }
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.subscription_link_card_button))
                    }
                }
            }
        }
    }
}
