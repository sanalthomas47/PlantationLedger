package com.santhomach.plantationledger.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.inventory.InventoryItem
import com.santhomach.plantationledger.data.inventory.inUseFor
import com.santhomach.plantationledger.ui.calculator.CalculatorState
import com.santhomach.plantationledger.ui.viewmodel.InventoryGroup
import com.santhomach.plantationledger.ui.viewmodel.InventoryItemViewModel
import com.santhomach.plantationledger.ui.viewmodel.InventoryViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

// -------------------------------------------------------------------------------------------------
// The list
// -------------------------------------------------------------------------------------------------

/** Equipment, tools and other items grouped by category, with + / − on each count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    onOpenItem: (Int) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenItem(0) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, contentDescription = "Add item") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Room at the bottom so the last row clears the "+" and calculator buttons.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.itemCount == 0) {
                item(key = "empty") { EmptyInventory() }
                return@LazyColumn
            }
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search items") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "summary") {
                Text(
                    text = "${plural(state.shownItems, "item")} · ${plural(state.shownCount, "piece")} in all",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (state.groups.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        "No items match \"${state.query.trim()}\".",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "group:${group.category.lowercase()}") { GroupHeader(group) }
                items(group.items, key = { it.id }) { item ->
                    InventoryRow(
                        item = item,
                        onOpen = { onOpenItem(item.id) },
                        onIncrement = { viewModel.increment(item) },
                        onDecrement = { viewModel.decrement(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInventory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("No items yet", fontWeight = FontWeight.SemiBold)
        Text(
            "Tap + to add equipment, tools and other things used on the estate.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GroupHeader(group: InventoryGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.category,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "${plural(group.items.size, "item")} · ${plural(group.totalCount, "pc")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun InventoryRow(
    item: InventoryItem,
    onOpen: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Edit ${item.name}", onClick = onOpen)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                val details = listOfNotNull(
                    item.inUseSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?.let(::usageText),
                    item.purchasePrice?.toBigDecimalOrNull()?.let { "₹${priceText(it)} each" }
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.notes.isNotBlank()) {
                    Text(
                        item.notes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onDecrement, enabled = item.count > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "One less ${item.name}")
            }
            Text(
                text = item.count.toString(),
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .semantics { contentDescription = "${item.count} ${item.name}" },
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            FilledTonalIconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "One more ${item.name}")
            }
        }
    }
}

/** e.g. "In use 2 yrs 3 mo", "In use since today" or "Not yet in use". */
private fun usageText(since: LocalDate): String = inUseFor(since).let { text ->
    when {
        text.first().isDigit() -> "In use $text"
        text == "Since today" -> "In use since today"
        else -> text
    }
}

private fun plural(n: Int, word: String) = "$n ${if (n == 1) word else word + "s"}"

private fun priceText(price: BigDecimal): String = CalculatorState.formatNumber(price.stripTrailingZeros().toPlainString())

// -------------------------------------------------------------------------------------------------
// One item's page
// -------------------------------------------------------------------------------------------------

/**
 * Adds a new item or edits one: name, category, count, in use since, indicative price and notes.
 * Saved to the database on Save; leaving with unsaved changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryItemScreen(
    onNavigateBack: () -> Unit,
    viewModel: InventoryItemViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDone) { if (state.isDone) onNavigateBack() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    fun leave() {
        if (state.hasChanges) confirmDiscard = true else onNavigateBack()
    }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New item" else "Edit item") },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.item != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete item")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.notFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("This item no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val form = state.form
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                    label = { Text("Item name *") },
                    placeholder = { Text("e.g. Power sprayer, Pruning knife") },
                    singleLine = true,
                    isError = form.name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = form.category,
                    onValueChange = { v -> viewModel.updateForm { it.copy(category = v) } },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = form.category.trim().equals(category, ignoreCase = true),
                            onClick = { viewModel.updateForm { it.copy(category = category) } },
                            label = { Text(category) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { viewModel.stepCount(-1) }, enabled = (form.countValue ?: 0) > 0) {
                        Icon(Icons.Default.Remove, contentDescription = "One less")
                    }
                    OutlinedTextField(
                        value = form.count,
                        onValueChange = { v -> viewModel.updateForm { it.copy(count = v.filter(Char::isDigit)) } },
                        label = { Text("Count *") },
                        singleLine = true,
                        isError = form.countError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    FilledTonalIconButton(onClick = { viewModel.stepCount(1) }) {
                        Icon(Icons.Default.Add, contentDescription = "One more")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("In use since", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(form.inUseSince?.format(DATE_FORMAT) ?: "Set date")
                        }
                        if (form.inUseSince != null) {
                            TextButton(onClick = { viewModel.updateForm { it.copy(inUseSince = null) } }) { Text("Clear") }
                        }
                    }
                    form.inUseSince?.let {
                        Text(
                            usageText(it),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = form.purchasePrice,
                    onValueChange = { v -> viewModel.updateForm { it.copy(purchasePrice = v) } },
                    label = { Text("Purchase price per item (₹)") },
                    supportingText = {
                        Text(if (form.priceError) "Enter a number, e.g. 1250" else "Indicative only. Not used in any expense or balance figure.")
                    },
                    singleLine = true,
                    isError = form.priceError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { v -> viewModel.updateForm { it.copy(notes = v) } },
                    label = { Text("Notes") },
                    placeholder = { Text("e.g. Brand, where it is kept, service dates") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.isSaving) "Saving..." else "Save") }
            }
        }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (state.form.inUseSince ?: LocalDate.now())
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        // The picker's dates are midnight UTC.
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.updateForm { it.copy(inUseSince = date) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this item have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onNavigateBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                Button(onClick = {
                    confirmDiscard = false
                    viewModel.save()
                }, enabled = state.canSave) { Text("Save") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text("\"${state.item?.name}\" will be removed from the inventory. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
