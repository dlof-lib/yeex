package com.yeex.dlof.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yeex.dlof.R
import com.yeex.dlof.data.model.PaymentCard
import com.yeex.dlof.data.model.SubscriptionPlan
import com.yeex.dlof.data.model.Subscriber
import com.yeex.dlof.data.repository.SubscriptionRepository
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexGold
import com.yeex.dlof.ui.theme.YeexBrandGradient
import kotlinx.coroutines.launch

/**
 * Subscriber-side "اشتراك" sheet, opened from another account's profile:
 * pick one of their [SubscriptionPlan]s, link a card first if none is on
 * file yet (see [LinkCardSheet]), then confirm. Also shows an
 * already-subscribed state with an unsubscribe action.
 */
@Composable
fun SubscribeSheet(
    visible: Boolean,
    ownerId: String,
    ownerName: String,
    subscriberId: String,
    subscriberIdentifier: String,
    plans: List<SubscriptionPlan>,
    repo: SubscriptionRepository = SubscriptionRepository(),
    onDismiss: () -> Unit,
    onSubscribed: () -> Unit
) {
    if (!visible) return
    var selectedPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var currentSub by remember { mutableStateOf<Subscriber?>(null) }
    var card by remember { mutableStateOf<PaymentCard?>(null) }
    var showLinkCard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId, subscriberId) {
        currentSub = runCatching { repo.mySubscriptionTo(ownerId, subscriberId) }.getOrNull()
        card = runCatching { repo.getCard(subscriberId) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title_named, ownerName), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )

            if (currentSub != null) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.subscription_subscribed_label), fontWeight = FontWeight.Bold)
                    Text(currentSub?.planName.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { repo.unsubscribe(ownerId, subscriberId) }
                            currentSub = null
                        }
                    }) { Text(stringResource(R.string.subscription_unsubscribe)) }
                }
            } else if (plans.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.subscription_no_plans), fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            stringResource(R.string.subscription_choose_plan),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(plans, key = { it.id }) { plan ->
                        SelectablePlanCard(plan = plan, selected = selectedPlan?.id == plan.id) { selectedPlan = plan }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = YeexDarkCard,
                            onClick = { showLinkCard = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = YeexAccent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (card != null) stringResource(R.string.subscription_card_linked) else stringResource(R.string.subscription_card_none),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (card != null) {
                                        Text("${card?.brand} •••• ${card?.last4}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { showLinkCard = true }) {
                                    Text(if (card != null) stringResource(R.string.subscription_manage_card) else stringResource(R.string.subscription_link_card_button))
                                }
                            }
                        }
                    }
                }

                Button(
                    enabled = selectedPlan != null && card != null && !saving,
                    onClick = {
                        val plan = selectedPlan ?: return@Button
                        saving = true
                        scope.launch {
                            runCatching { repo.subscribe(plan, subscriberId, subscriberIdentifier) }
                            saving = false
                            onSubscribed()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(
                        if (card == null) stringResource(R.string.subscription_link_card_and_subscribe)
                        else stringResource(R.string.subscription_confirm_subscribe)
                    )
                }
            }
        }
    }

    LinkCardSheet(
        visible = showLinkCard,
        uid = subscriberId,
        repo = repo,
        onDismiss = { showLinkCard = false },
        onLinked = {
            showLinkCard = false
            scope.launch { card = runCatching { repo.getCard(subscriberId) }.getOrNull() }
        }
    )
}

@Composable
private fun SelectablePlanCard(plan: SubscriptionPlan, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = YeexDarkCard,
        border = if (selected) BorderStroke(1.5.dp, YeexAccent) else null,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(YeexBrandGradient, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(plan.priceLabel, style = MaterialTheme.typography.labelLarge, color = YeexAccent, fontWeight = FontWeight.Bold)
            }
            if (plan.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(plan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan.features.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                plan.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = YeexGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(feature, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
