package com.ricardo.txtreader.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ricardo.txtreader.tts.PasteReaderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteReaderApp(viewModel: PasteReaderViewModel) {
    val state = viewModel.uiState
    val clipboard = LocalClipboardManager.current
    val editorValue = TextFieldValue(
        text = state.text,
        selection = TextRange(state.selectionStart, state.selectionEnd)
    )
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val highlightTransformation = remember(state.highlightStart, state.highlightEnd, highlightColor) {
        WordHighlightTransformation(
            highlightStart = state.highlightStart,
            highlightEnd = state.highlightEnd,
            highlightColor = highlightColor
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PasteReader") },
                actions = {
                    IconButton(
                        onClick = viewModel::clearText,
                        enabled = state.text.isNotBlank()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Borrar texto")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editorValue,
                onValueChange = { value ->
                    viewModel.onEditorValueChange(
                        text = value.text,
                        selectionStart = value.selection.start,
                        selectionEnd = value.selection.end
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif
                ),
                leadingIcon = {
                    Icon(Icons.Default.TextFields, contentDescription = null)
                },
                placeholder = { Text("Pega aquí el texto que quieras escuchar") },
                minLines = 12,
                visualTransformation = highlightTransformation
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        clipboard.getText()?.text?.let(viewModel::pasteText)
                            ?: viewModel.onClipboardEmpty()
                    }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text("Pegar")
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = viewModel::stopSpeech,
                    enabled = state.speaking || state.paused
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener")
                }
                IconButton(
                    onClick = {
                        when {
                            state.speaking -> viewModel.pauseSpeech()
                            state.paused -> viewModel.resumeSpeech()
                            else -> viewModel.startSpeech()
                        }
                    },
                    enabled = state.ttsReady && (state.text.isNotBlank() || state.paused)
                ) {
                    Icon(
                        imageVector = if (state.speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.speaking) "Pausar" else "Reproducir"
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.text.length} caracteres",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Velocidad",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = state.speechRate,
                        onValueChange = viewModel::changeSpeechRate,
                        valueRange = 0.6f..1.6f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", state.speechRate),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private class WordHighlightTransformation(
    private val highlightStart: Int?,
    private val highlightEnd: Int?,
    private val highlightColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val start = highlightStart ?: return TransformedText(text, OffsetMapping.Identity)
        val end = highlightEnd ?: return TransformedText(text, OffsetMapping.Identity)
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        if (safeStart == safeEnd) return TransformedText(text, OffsetMapping.Identity)

        val highlightedText = buildAnnotatedString {
            append(text)
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = safeStart,
                end = safeEnd
            )
        }
        return TransformedText(highlightedText, OffsetMapping.Identity)
    }
}
