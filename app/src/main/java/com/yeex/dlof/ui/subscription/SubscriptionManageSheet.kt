package com.yeex.dlof.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yeex.dlof.R
import com.yeex.dlof.data.model.SubscriptionPlan
import com.yeex.dlof.data.repository.SubscriptionRepository
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexGold
import com.yeex.dlof.ui.theme.YeexBrandGradient
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Owner-side "إدارة الاشتراك": full-screen dialog listing every plan this
 * account has published, with create/edit/delete. Any account can add as
 * many plans as it wants — the owner freely names each one and writes its
 * own perk list (see [SubscriptionPlan]'s doc comment); yeex doesn't
 * prescribe what counts as a "feature".
 */
@Composable
fun SubscriptionManageSheet(
    visible: Boolean,
    ownerId: String,
    repo: SubscriptionRepository = SubscriptionRepository(),
    onDismiss: () -> Unit
) {
    if (!visible) return
    var plans by remember { mutableStateOf<List<SubscriptionPlan>>(emptyList()) }
    var editing by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId) {
        repo.observePlans(ownerId).catch { }.collect { plans = it }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_manage), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.subscription_create_plan), tint = YeexAccent)
                    }
                }
            )

            if (plans.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Workspaces, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.subscription_no_plans), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.subscription_no_plans_owner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { editing = null; showEditor = true }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.subscription_create_plan))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(plans, key = { it.id }) { plan ->
                        PlanCard(
                            plan = plan,
                            onEdit = { editing = plan; showEditor = true },
                            onDelete = { scope.launch { runCatching { repo.deletePlan(ownerId, plan.id) } } }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        PlanEditorDialog(
            ownerId = ownerId,
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { plan ->
                scope.launch {
                    runCatching { repo.savePlan(plan) }
                    showEditor = false
                }
            }
        )
    }
}

@Composable
private fun PlanCard(plan: SubscriptionPlan, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = YeexDarkCard,
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(YeexBrandGradient, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(plan.name.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(plan.priceLabel, style = MaterialTheme.typography.labelLarge, color = YeexAccent, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.subscription_delete_plan), tint = MaterialTheme.colorScheme.error)
                }
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

@Composable
private fun PlanEditorDialog(
    ownerId: String,
    initial: SubscriptionPlan?,
    onDismiss: () -> Unit,
    onSave: (SubscriptionPlan) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.priceLabel ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    val features = remember { mutableStateListOf<String>().apply { addAll(initial?.features ?: emptyList()) } }
    var newFeature by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.subscription_create_plan),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text(stringResource(R.string.subscription_plan_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { if (it.length <= 30) price = it },
                    label = { Text(stringResource(R.string.subscription_plan_price_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 150) description = it },
                    label = { Text(stringResource(R.string.subscription_plan_description_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.subscription_plan_features_hint), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                features.forEachIndexed { index, feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = YeexGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(feature, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { features.removeAt(index) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        value = newFeature,
                        onValueChange = { if (it.length <= 60) newFeature = it },
                        placeholder = { Text(stringResource(R.string.subscription_add_feature)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newFeature.isNotBlank()) { features.add(newFeature.trim()); newFeature = "" }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.subscription_add_feature), tint = YeexAccent)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(
                                (initial ?: SubscriptionPlan()).copy(
                                    ownerId = ownerId,
                                    name = name.trim(),
                                    priceLabel = price.trim(),
                                    description = description.trim(),
                                    features = features.toList()
                                )
                            )
                        }
                    ) { Text(stringResource(R.string.subscription_save_plan)) }
                }
            }
        }
    }
}
