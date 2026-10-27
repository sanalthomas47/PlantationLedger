package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.CombinationCategory
import com.santhomach.plantationledger.data.schedule.ScheduleTask
import com.santhomach.plantationledger.ui.viewmodel.MonthChecklist
import com.santhomach.plantationledger.ui.viewmodel.ScheduleUiState
import com.santhomach.plantationledger.ui.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** A task being added to [month], or [task] being edited. */
private data class TaskEdit(val month: Int, val task: ScheduleTask?)

/** Expanded section keys ("month_10", "FERTILIZER" ...), kept across rotation and navigation. */
private val expandedSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() }
)

/**
 * The Schedule: a Jan-Dec checklist of essential work (ticked per year) and the fertilizer, foliar
 * and pesticide combination cards. Tap a card to open its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    onOpenCard: (Int) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val today = remember { LocalDate.now() }
    val expanded = rememberSaveable(saver = expandedSaver) {
        mutableStateListOf(
            "checklist",
            "month_${today.monthValue}",
            *CombinationCategory.entries.map { it.name }.toTypedArray()
        )
    }

    fun toggle(key: String) {
        if (key in expanded) expanded.remove(key) else expanded.add(key)
    }

    var taskEdit by remember { mutableStateOf<TaskEdit?>(null) }
    var newCardFor by remember { mutableStateOf<CombinationCategory?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            checklistSection(
                state = state,
                isOpen = { it in expanded },
                onToggle = ::toggle,
                onYear = viewModel::changeYear,
                onDone = viewModel::setDone,
                onEdit = { taskEdit = it }
            )
            CombinationCategory.entries.forEach { category ->
                cardSection(
                    category = category,
                    cards = state.cards[category].orEmpty(),
                    open = category.name in expanded,
                    onToggle = { toggle(category.name) },
                    onAdd = { newCardFor = category },
                    onOpenCard = onOpenCard
                )
            }
        }
    }

    taskEdit?.let { edit ->
        TaskDialog(
            edit = edit,
            onDismiss = { taskEdit = null },
            onSave = { title ->
                if (edit.task == null) viewModel.addTask(
                    edit.month,
                    title
                ) else viewModel.renameTask(edit.task, title)
                taskEdit = null
            },
            onDelete = {
                edit.task?.let(viewModel::deleteTask)
                taskEdit = null
            }
        )
    }

    newCardFor?.let { category ->
        NameDialog(
            title = "New ${category.singular}",
            label = "Combination group name",
            onDismiss = { newCardFor = null },
            onSave = { name ->
                viewModel.addCard(category, name, onOpenCard)
                newCardFor = null
            }
        )
    }
}

private fun monthName(month: Int) = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

private fun LazyListScope.checklistSection(
    state: ScheduleUiState,
    isOpen: (String) -> Boolean,
    onToggle: (String) -> Unit,
    onYear: (Int) -> Unit,
    onDone: (ScheduleTask, Boolean) -> Unit,
    onEdit: (TaskEdit) -> Unit
) {
    val open = isOpen("checklist")
    item(key = "checklist_header") {
        SectionHeader(
            title = "Monthly work",
            subtitle = "Essential work for each month. Ticks are kept for each year.",
            open = open,
            onToggle = { onToggle("checklist") }
        )
    }
    if (!open) return
    item(key = "checklist_year") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onYear(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous year"
                )
            }
            Text(
                text = state.year.toString(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onYear(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
            }
        }
    }
    state.months.forEach { month ->
        item(key = "month_${month.month}") {
            MonthBlock(
                month = month,
                open = isOpen("month_${month.month}"),
                onToggle = { onToggle("month_${month.month}") },
                onDone = onDone,
                onEdit = { task -> onEdit(TaskEdit(month.month, task)) },
                onAdd = { onEdit(TaskEdit(month.month, null)) }
            )
        }
    }
}

@Composable
private fun MonthBlock(
    month: MonthChecklist,
    open: Boolean,
    onToggle: () -> Unit,
    onDone: (ScheduleTask, Boolean) -> Unit,
    onEdit: (ScheduleTask) -> Unit,
    onAdd: () -> Unit
) {
    val complete = month.tasks.isNotEmpty() && month.doneCount == month.tasks.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (open) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = monthName(month.month),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (month.tasks.isEmpty()) "No tasks"
                else "${month.doneCount}/${month.tasks.size} done" + if (complete) " ✓" else "",
                color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            month.tasks.forEach { task ->
                val done = task.id in month.done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDone(task, !done) }
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = done, onCheckedChange = { onDone(task, it) })
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { onEdit(task) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit task",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add task")
            }
        }
    }
}

private fun LazyListScope.cardSection(
    category: CombinationCategory,
    cards: List<CombinationCard>,
    open: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onOpenCard: (Int) -> Unit
) {
    item(key = "section_${category.name}") {
        SectionHeader(
            title = "${category.title} (${cards.size})",
            subtitle = "Tap a card to see or edit it.",
            open = open,
            onToggle = onToggle,
            action = {
                TextButton(onClick = onAdd) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        )
    }
    if (!open) return
    if (cards.isEmpty()) {
        item(key = "empty_${category.name}") {
            Text(
                text = "No ${category.singular}s yet. Tap Add to create one.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    cards.forEach { card ->
        item(key = "card_${card.id}") {
            CombinationCardItem(
                card,
                onClick = { onOpenCard(card.id) })
        }
    }
}

@Composable
private fun CombinationCardItem(card: CombinationCard, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = card.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (card.purpose.isNotBlank()) {
                    Text(
                        text = card.purpose,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = card.combination.ifBlank { "No combination entered yet" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    open: Boolean,
    onToggle: () -> Unit,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (open) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        action()
    }
}

@Composable
private fun TaskDialog(
    edit: TaskEdit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(edit.task?.title ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text("\"${edit.task?.title}\" is removed from ${monthName(edit.month)}, with its ticks for every year.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit.task == null) "Add task for ${monthName(edit.month)}" else "Edit task") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Work to be done") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (edit.task != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
