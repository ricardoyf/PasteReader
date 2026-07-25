package com.ricardo.txtreader.navigation

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteReaderApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val text = editorValue.text
    var message by remember { mutableStateOf("Pega un texto y pulsa play.") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var chunks by remember { mutableStateOf<List<SpeechChunk>>(emptyList()) }
    var chunkIndex by remember { mutableIntStateOf(0) }
    var sessionId by remember { mutableIntStateOf(0) }
    var speechRate by remember { mutableFloatStateOf(1.2f) }
    var ttsHighlightStart by remember { mutableStateOf<Int?>(null) }
    var ttsHighlightEnd by remember { mutableStateOf<Int?>(null) }
    var resumeOffset by remember { mutableIntStateOf(0) }
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val highlightTransformation = remember(ttsHighlightStart, ttsHighlightEnd, highlightColor) {
        WordHighlightTransformation(
            highlightStart = ttsHighlightStart,
            highlightEnd = ttsHighlightEnd,
            highlightColor = highlightColor
        )
    }

    fun stopSpeech(resetChunks: Boolean = true) {
        sessionId += 1
        tts?.stop()
        speaking = false
        paused = false
        ttsHighlightStart = null
        ttsHighlightEnd = null
        if (resetChunks) {
            chunks = emptyList()
            chunkIndex = 0
            resumeOffset = 0
        }
    }

    fun speakChunk(index: Int) {
        val engine = tts ?: return
        val chunk = chunks.getOrNull(index)
        if (chunk == null) {
            speaking = false
            paused = false
            chunkIndex = 0
            ttsHighlightStart = null
            ttsHighlightEnd = null
            message = "Lectura terminada."
            return
        }

        chunkIndex = index
        speaking = true
        paused = false
        resumeOffset = chunk.startOffset
        editorValue = editorValue.copy(selection = TextRange(chunk.startOffset))
        ttsHighlightStart = chunk.startOffset
        ttsHighlightEnd = chunk.endOffset
        message = "Leyendo ${index + 1}/${chunks.size}"
        engine.setSpeechRate(speechRate)
        engine.speak(
            chunk.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "pastereader-$sessionId-$index"
        )
    }

    fun startSpeech() {
        if (!ttsReady) {
            message = "El TTS español de Android aún no está listo."
            return
        }
        if (text.isBlank()) {
            message = "No hay texto para leer."
            return
        }

        stopSpeech(resetChunks = false)
        val nextChunks = splitSpeechText(text, startOffset = 0)
        if (nextChunks.isEmpty()) {
            message = "No hay texto para leer."
            return
        }
        chunks = nextChunks
        chunkIndex = 0
        resumeOffset = 0
        sessionId += 1
        speakChunk(0)
    }

    fun pauseSpeech() {
        if (!speaking) return
        resumeOffset = (ttsHighlightStart ?: chunks.getOrNull(chunkIndex)?.startOffset ?: 0)
            .coerceIn(0, text.length)
        sessionId += 1
        tts?.stop()
        speaking = false
        paused = true
        message = "Pausado ${chunkIndex + 1}/${chunks.size}"
    }

    fun resumeSpeech() {
        if (text.isBlank()) {
            startSpeech()
            return
        }
        val nextChunks = splitSpeechText(text, startOffset = resumeOffset)
        if (nextChunks.isEmpty()) {
            startSpeech()
            return
        }
        chunks = nextChunks
        chunkIndex = 0
        sessionId += 1
        speakChunk(0)
    }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            scope.launch {
                if (status == TextToSpeech.SUCCESS) {
                    val result = engine?.setLanguage(Locale("es", "ES"))
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    message = if (ttsReady) "Pega un texto y pulsa play." else "El TTS español no está disponible en este móvil."
                } else {
                    ttsReady = false
                    message = "No se pudo iniciar el TTS de Android."
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    val parts = utteranceId?.split("-").orEmpty()
                    val spokenSession = parts.getOrNull(1)?.toIntOrNull()
                    val spokenIndex = parts.getOrNull(2)?.toIntOrNull()
                    if (spokenSession != sessionId || spokenIndex != chunkIndex) return@launch
                    val chunk = chunks.getOrNull(spokenIndex) ?: return@launch
                    resumeOffset = chunk.startOffset
                    editorValue = editorValue.copy(selection = TextRange(chunk.startOffset))
                    ttsHighlightStart = chunk.startOffset
                    ttsHighlightEnd = chunk.endOffset
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    val parts = utteranceId?.split("-").orEmpty()
                    val spokenSession = parts.getOrNull(1)?.toIntOrNull()
                    val spokenIndex = parts.getOrNull(2)?.toIntOrNull()
                    if (spokenSession != sessionId || spokenIndex != chunkIndex) return@launch
                    val nextIndex = chunkIndex + 1
                    if (nextIndex < chunks.size) {
                        speakChunk(nextIndex)
                    } else {
                        speaking = false
                        paused = false
                        chunkIndex = 0
                        chunks = emptyList()
                        resumeOffset = 0
                        ttsHighlightStart = null
                        ttsHighlightEnd = null
                        message = "Lectura terminada."
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    speaking = false
                    paused = false
                    message = "Error durante la lectura."
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                scope.launch {
                    val parts = utteranceId?.split("-").orEmpty()
                    val spokenSession = parts.getOrNull(1)?.toIntOrNull()
                    val spokenIndex = parts.getOrNull(2)?.toIntOrNull()
                    if (spokenSession != sessionId || spokenIndex != chunkIndex) return@launch
                    val chunk = chunks.getOrNull(spokenIndex) ?: return@launch
                    val absoluteStart = (chunk.startOffset + start)
                        .coerceIn(chunk.startOffset, chunk.endOffset)
                    val absoluteEnd = (chunk.startOffset + end)
                        .coerceIn(absoluteStart, chunk.endOffset)
                    resumeOffset = absoluteStart
                    editorValue = editorValue.copy(selection = TextRange(absoluteEnd))
                    ttsHighlightStart = absoluteStart
                    ttsHighlightEnd = absoluteEnd
                }
            }
        })
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PasteReader") },
                actions = {
                    IconButton(
                        onClick = {
                            stopSpeech()
                            editorValue = TextFieldValue()
                            message = "Texto borrado."
                        },
                        enabled = text.isNotBlank()
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
                    if (speaking || paused) stopSpeech()
                    editorValue = value
                    if (value.text.isBlank()) message = "Pega un texto y pulsa play."
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
                        clipboard.getText()?.text?.let { pasted ->
                            if (speaking || paused) stopSpeech()
                            editorValue = TextFieldValue(
                                text = pasted,
                                selection = TextRange(pasted.length)
                            )
                            message = "Texto pegado."
                        } ?: run {
                            message = "El portapapeles está vacío."
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text("Pegar")
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { stopSpeech(); message = "Detenido." },
                    enabled = speaking || paused
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener")
                }
                IconButton(
                    onClick = { if (speaking) pauseSpeech() else if (paused) resumeSpeech() else startSpeech() },
                    enabled = ttsReady && (text.isNotBlank() || paused)
                ) {
                    Icon(
                        imageVector = if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (speaking) "Pausar" else "Reproducir"
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
                        text = message,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${text.length} caracteres",
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
                        value = speechRate,
                        onValueChange = { value ->
                            speechRate = value.coerceIn(0.6f, 1.6f)
                            tts?.setSpeechRate(speechRate)
                        },
                        valueRange = 0.6f..1.6f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", speechRate),
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

private data class SpeechChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

private fun splitSpeechText(
    text: String,
    startOffset: Int = 0,
    maxChars: Int = 2800
): List<SpeechChunk> {
    if (text.isBlank()) return emptyList()

    val chunks = mutableListOf<SpeechChunk>()
    var cursor = startOffset.coerceIn(0, text.length)
    while (cursor < text.length) {
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        if (cursor >= text.length) break

        val hardEnd = (cursor + maxChars).coerceAtMost(text.length)
        var end = if (hardEnd == text.length) hardEnd else preferredBreak(text, cursor, hardEnd)
        if (end <= cursor) end = hardEnd
        while (end > cursor && text[end - 1].isWhitespace()) end--
        if (end > cursor) {
            chunks += SpeechChunk(
                text = text.substring(cursor, end),
                startOffset = cursor,
                endOffset = end
            )
        }
        cursor = end.coerceAtLeast(cursor + 1)
    }
    return chunks
}

private fun preferredBreak(text: String, start: Int, hardEnd: Int): Int {
    val minUseful = start + 240
    val sentenceBreak = (hardEnd - 1 downTo minUseful).firstOrNull { index ->
        val char = text[index]
        (char == '.' || char == '!' || char == '?' || char == '\n') &&
            index + 1 < text.length &&
            text[index + 1].isWhitespace()
    }
    if (sentenceBreak != null) return sentenceBreak + 1

    return (hardEnd - 1 downTo minUseful).firstOrNull { text[it].isWhitespace() } ?: hardEnd
}
