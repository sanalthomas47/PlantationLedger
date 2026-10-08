package com.santhomach.plantationledger.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val ButtonSize = 56.dp
private val PanelWidth = 288.dp

/**
 * A draggable calculator button that floats above every main screen. Tapping it opens a small
 * draggable calculator panel; closing the panel brings the button back. Place it once, on top of
 * the app's navigation, so it appears on every screen.
 */
@Composable
fun FloatingCalculator(viewModel: CalculatorViewModel, modifier: Modifier = Modifier) {
    // Keep clear of the status and navigation bars.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val density = LocalDensity.current
        val area = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }

        if (viewModel.isOpen) {
            var panelSize by remember { mutableStateOf(IntSize(with(density) { PanelWidth.roundToPx() }, 0)) }
            val default = Offset(
                ((area.width - panelSize.width) / 2f),
                area.height * 0.15f
            )
            val position = clamp(viewModel.panelOffset ?: default, panelSize, area)
            CalculatorPanel(
                viewModel = viewModel,
                onDrag = { delta ->
                    val latest = viewModel.panelOffset ?: default
                    viewModel.panelOffset = clamp(latest + delta, panelSize, area)
                },
                modifier = Modifier
                    .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .onSizeChanged { panelSize = it }
            )
        } else {
            val size = with(density) { ButtonSize.roundToPx() }
            val buttonSize = IntSize(size, size)
            // Default: left edge, two-thirds down, away from the screens' "+" buttons on the right.
            val default = Offset(with(density) { 12.dp.toPx() }, area.height * 0.66f)
            val position = clamp(viewModel.buttonOffset ?: default, buttonSize, area)
            val currentDefault by rememberUpdatedState(default)
            val currentArea by rememberUpdatedState(area)

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .size(ButtonSize)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val latest = clamp(viewModel.buttonOffset ?: currentDefault, buttonSize, currentArea)
                            viewModel.buttonOffset = clamp(latest + drag, buttonSize, currentArea)
                        }
                    }
                    .clickable(onClickLabel = "Open calculator") { viewModel.open() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Calculate, contentDescription = "Calculator")
                }
            }
        }
    }
}

/** Keeps something of [size] fully inside [area]. */
private fun clamp(offset: Offset, size: IntSize, area: IntSize): Offset = Offset(
    offset.x.coerceIn(0f, (area.width - size.width).coerceAtLeast(0).toFloat()),
    offset.y.coerceIn(0f, (area.height - size.height).coerceAtLeast(0).toFloat())
)

@Composable
private fun CalculatorPanel(
    viewModel: CalculatorViewModel,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    val currentOnDrag by rememberUpdatedState(onDrag)

    Card(
        modifier = modifier
            .width(PanelWidth)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // ---- Title bar: drag here to move the panel ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        currentOnDrag(drag)
                    }
                }
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to move",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Calculator",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = viewModel::toggleHistory) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = if (viewModel.showHistory) "Hide history" else "Show history",
                    tint = if (viewModel.showHistory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::close) {
                Icon(Icons.Filled.Close, contentDescription = "Close calculator")
            }
        }

        // ---- Display ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            val topLine = if (state.justEvaluated) state.history.firstOrNull()?.let { "${it.expression} =" } ?: ""
            else state.expression
            Text(
                text = topLine.ifEmpty { " " },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )
            Text(
                text = when {
                    state.error != null -> state.error
                    state.tokens.isEmpty() -> "0"
                    else -> state.preview ?: state.expression
                },
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontSize = if (state.error != null) 18.sp else 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )
        }

        // ---- Keypad ----
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyRow {
                Key("C", KeyKind.Clear, onClick = viewModel::clear)
                Key("⌫", KeyKind.Function, description = "Delete", onClick = viewModel::backspace)
                Key("÷", KeyKind.Operator) { viewModel.operator(Operator.DIVIDE) }
                Key("×", KeyKind.Operator) { viewModel.operator(Operator.MULTIPLY) }
            }
            KeyRow {
                Key("7") { viewModel.digit('7') }
                Key("8") { viewModel.digit('8') }
                Key("9") { viewModel.digit('9') }
                Key("−", KeyKind.Operator) { viewModel.operator(Operator.SUBTRACT) }
            }
            KeyRow {
                Key("4") { viewModel.digit('4') }
                Key("5") { viewModel.digit('5') }
                Key("6") { viewModel.digit('6') }
                Key("+", KeyKind.Operator) { viewModel.operator(Operator.ADD) }
            }
            KeyRow {
                Key("1") { viewModel.digit('1') }
                Key("2") { viewModel.digit('2') }
                Key("3") { viewModel.digit('3') }
                Key(".", onClick = viewModel::decimalPoint)
            }
            KeyRow {
                Key("0", weight = 2f) { viewModel.digit('0') }
                Key("=", KeyKind.Equals, weight = 2f, onClick = viewModel::calculate)
            }
        }

        // ---- Session history ----
        if (viewModel.showHistory) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (this session)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = viewModel::clearHistory, enabled = state.history.isNotEmpty()) {
                    Text("Clear")
                }
            }
            if (state.history.isEmpty()) {
                Text(
                    text = "No calculations yet",
                    modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .padding(horizontal = 14.dp)
                ) {
                    items(state.history) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = entry.expression,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = "= ${entry.result}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private enum class KeyKind { Digit, Operator, Function, Clear, Equals }

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun RowScope.Key(
    label: String,
    kind: KeyKind = KeyKind.Digit,
    weight: Float = 1f,
    description: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (kind) {
        KeyKind.Digit -> colors.surfaceVariant to colors.onSurfaceVariant
        KeyKind.Operator -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyKind.Function -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyKind.Clear -> colors.errorContainer to colors.onErrorContainer
        KeyKind.Equals -> colors.primary to colors.onPrimary
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClickLabel = description ?: label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = description, tint = foreground)
        } else {
            Text(
                text = label,
                color = foreground,
                fontSize = 20.sp,
                fontWeight = if (kind == KeyKind.Digit) FontWeight.Normal else FontWeight.SemiBold
            )
        }
    }
}
