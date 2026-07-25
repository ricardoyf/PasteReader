package com.ricardo.txtreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.TextView
import com.ricardo.txtreader.ui.components.EmptyStateCard
import com.ricardo.txtreader.ui.components.MarkdownText
import com.ricardo.txtreader.ui.components.SelectableReaderText
import com.ricardo.txtreader.ui.viewmodel.ReaderUiState
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onNextFile: () -> Unit,
    onPreviousFile: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMarkAsUnread: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onOpenLibrary: () -> Unit,
    ttsReady: Boolean,
    ttsSpeaking: Boolean,
    ttsPaused: Boolean,
    ttsMessage: String?,
    ttsHighlightStart: Int?,
    ttsHighlightEnd: Int?,
    ttsPositionLocked: Boolean,
    speechRate: Float,
    sleepTimerMinutes: Int?,
    onStartReading: (String, Int) -> Unit,
    onStartReadingFromText: (String, Int) -> Unit,
    onPauseReading: () -> Unit,
    onResumeReading: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onManualPositionChanged: (String, Int, Int) -> Unit
) {
    var barsVisible by remember { mutableStateOf(true) }
    var fontBarVisible by remember { mutableStateOf(false) }
    var readerTextView by remember { mutableStateOf<TextView?>(null) }
    val readerScrollState = rememberScrollState()
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f).toArgb()
    val restorePosition = state.restoredReadingPosition
    val highlightedStart = ttsHighlightStart ?: restorePosition?.characterOffset
    val highlightedEnd = ttsHighlightEnd ?: highlightedStart?.let { (it + 180).coerceAtMost(readerTextView?.text?.length ?: state.content.length) }

    LaunchedEffect(state.selectedFile?.uri, restorePosition?.updatedAt, readerTextView, state.fontSizeSp) {
        val view = readerTextView ?: return@LaunchedEffect
        val position = restorePosition ?: return@LaunchedEffect
        repeat(12) {
            if (view.layout != null && view.text.isNotEmpty()) return@repeat
            delay(80)
        }
        if (view.layout == null) return@LaunchedEffect
        val text = view.text?.toString().orEmpty()
        if (text.isBlank()) return@LaunchedEffect
        val offset = resolveRestoredOffset(text, position.characterOffset, position.textPreview)
        val y = centeredScrollYForOffset(
            view = view,
            offset = offset,
            viewportHeight = readerScrollState.viewportSize
        )
        readerScrollState.scrollTo(y.coerceIn(0, readerScrollState.maxValue))
    }

    LaunchedEffect(readerScrollState.isScrollInProgress, state.selectedFile?.uri, readerTextView, ttsPositionLocked) {
        if (readerScrollState.isScrollInProgress) return@LaunchedEffect
        if (ttsPositionLocked) return@LaunchedEffect
        delay(750)
        if (ttsPositionLocked) return@LaunchedEffect
        val view = readerTextView ?: return@LaunchedEffect
        val offset = visibleCenterOffset(view, readerScrollState.value, readerScrollState.viewportSize)
        onManualPositionChanged(view.text?.toString().orEmpty(), offset, readerScrollState.value)
    }

    val verticalGestureModifier = Modifier.pointerInput(state.selectedFileIndex) {
        var accumulatedDrag = 0f
        detectVerticalDragGestures(
            onVerticalDrag = { _, dragAmount ->
                accumulatedDrag += dragAmount
            },
            onDragEnd = {
                when {
                    accumulatedDrag < -80f -> barsVisible = false
                    accumulatedDrag > 80f -> barsVisible = true
                }
                accumulatedDrag = 0f
            }
        )
    }

    Scaffold(
        topBar = {
            if (barsVisible) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.selectedFile?.name ?: "Lector")
                            Text(
                                text = if (state.selectedFileIndex >= 0 && state.allTxtFiles.isNotEmpty()) {
                                    "${state.selectedFileIndex + 1} / ${state.allTxtFiles.size}"
                                } else {
                                    "Sin archivo"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        when {
                            ttsSpeaking -> {
                                IconButton(onClick = onPauseReading) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pausar lectura")
                                }
                            }
                            ttsPaused -> {
                                IconButton(onClick = onResumeReading, enabled = ttsReady) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Reanudar lectura")
                                }
                            }
                            else -> {
                                IconButton(
                                    onClick = {
                                        val text = readerTextView?.text?.toString().orEmpty().ifBlank { state.content }
                                        onStartReading(text, state.currentCharacterOffset)
                                    },
                                    enabled = ttsReady
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Leer en voz alta")
                                }
                            }
                        }
                        IconButton(onClick = { fontBarVisible = !fontBarVisible }) {
                            Icon(Icons.Default.TextIncrease, contentDescription = "Mostrar tamaño")
                        }
                        if (state.selectedFile?.isRead == true) {
                            TextButton(onClick = onMarkAsUnread) {
                                Icon(Icons.Default.RemoveDone, contentDescription = "No leído", modifier = Modifier.size(16.dp))
                                Text("No leído")
                            }
                        } else {
                            TextButton(onClick = onMarkAsRead) {
                                Icon(Icons.Default.Done, contentDescription = "Leído", modifier = Modifier.size(16.dp))
                                Text("Leído")
                            }
                        }
                        IconButton(onClick = onExportJson) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar backup JSON")
                        }
                        IconButton(onClick = onImportJson) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Importar backup JSON")
                        }
                        IconButton(onClick = onOpenLibrary) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Biblioteca")
                        }
                    }
                )
            }
        },
    ) { padding ->
        if (state.selectedFile == null) {
            EmptyStateCard(
                title = "No hay archivo abierto",
                description = "Vuelve a la biblioteca y elige un .txt.",
                actionLabel = "Ir a biblioteca",
                onAction = onOpenLibrary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp)
                .navigationBarsPadding()
        ) {
            if (barsVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (state.selectedFile.isRead) "✓ Leído" else "Pendiente",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.selectedFile.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ttsMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (barsVisible && fontBarVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = state.fontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 14f..30f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${state.fontSizeSp.toInt()}sp",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (barsVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CompactControlButton(onClick = { onSpeechRateChange(speechRate - 0.1f) }) {
                        Text("-")
                    }
                    Text(
                        text = speechRateLabel(speechRate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    CompactControlButton(onClick = { onSpeechRateChange(speechRate + 0.1f) }) {
                        Text("+")
                    }
                    listOf(15, 30, 45).forEach { minutes ->
                        CompactControlButton(onClick = { onSetSleepTimer(minutes) }) {
                            Text(if (sleepTimerMinutes == minutes) "${minutes}m ✓" else "${minutes}m")
                        }
                    }
                    CompactControlButton(onClick = { onSetSleepTimer(null) }) {
                        Text(if (sleepTimerMinutes == null) "Off ✓" else "Off")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(verticalGestureModifier)
                    .padding(top = if (barsVisible) 6.dp else 0.dp, bottom = if (barsVisible) 6.dp else 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .verticalScroll(readerScrollState)
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    if (state.isMarkdownContent) {
                        MarkdownText(
                            markdown = state.content,
                            fontSize = state.fontSizeSp.sp,
                            modifier = Modifier.fillMaxWidth(),
                            highlightStart = highlightedStart,
                            highlightEnd = highlightedEnd,
                            highlightColor = highlightColor,
                            onTextViewReady = { readerTextView = it },
                            onReadFromSelection = onStartReadingFromText
                        )
                    } else {
                        SelectableReaderText(
                            text = state.content,
                            fontSize = state.fontSizeSp.sp,
                            modifier = Modifier.fillMaxWidth(),
                            highlightStart = highlightedStart,
                            highlightEnd = highlightedEnd,
                            highlightColor = highlightColor,
                            onTextViewReady = { readerTextView = it },
                            onReadFromSelection = onStartReadingFromText
                        )
                    }
                }
            }

            if (barsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomFileNavButton(
                        onClick = onPreviousFile
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Archivo anterior")
                    }
                    BottomFileNavButton(
                        onClick = onNextFile
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Archivo siguiente")
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomFileNavButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun CompactControlButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 0.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        content = content
    )
}

private fun visibleCenterOffset(view: TextView, scrollY: Int, viewportHeight: Int): Int {
    val layout = view.layout ?: return 0
    val centerY = (scrollY + viewportHeight / 2).coerceAtLeast(0)
    val line = layout.getLineForVertical(centerY.coerceAtMost(view.height.coerceAtLeast(0)))
    return layout.getLineStart(line).coerceIn(0, view.text?.length ?: 0)
}

private fun resolveRestoredOffset(text: String, savedOffset: Int, preview: String): Int {
    if (text.isBlank()) return 0
    val safeOffset = savedOffset.coerceIn(0, text.length)
    if (preview.isBlank()) return safeOffset

    val localStart = (safeOffset - preview.length).coerceAtLeast(0)
    val localEnd = (safeOffset + preview.length).coerceAtMost(text.length)
    val local = text.substring(localStart, localEnd)
    if (local.contains(preview)) return safeOffset

    val found = text.indexOf(preview)
    return if (found >= 0) (found + preview.length / 2).coerceIn(0, text.length) else safeOffset
}

private fun centeredScrollYForOffset(view: TextView, offset: Int, viewportHeight: Int): Int {
    val layout = view.layout ?: return 0
    val safeOffset = offset.coerceIn(0, view.text?.length ?: 0)
    val line = layout.getLineForOffset(safeOffset)
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)
    val lineCenter = lineTop + (lineBottom - lineTop) / 2
    val usableViewport = viewportHeight.takeIf { it > 0 } ?: view.height
    return (lineCenter - usableViewport / 2).coerceAtLeast(0)
}

private fun speechRateLabel(rate: Float): String =
    String.format(Locale.US, "%.1fx", rate)
