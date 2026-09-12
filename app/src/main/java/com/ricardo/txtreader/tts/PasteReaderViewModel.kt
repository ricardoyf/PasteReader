package com.ricardo.txtreader.tts

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val DEFAULT_MESSAGE = "Pega un texto y pulsa play."

data class PasteReaderUiState(
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val message: String = DEFAULT_MESSAGE,
    val ttsReady: Boolean = false,
    val mode: PlaybackMode = PlaybackMode.IDLE,
    val speechRate: Float = 1.2f,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null
) {
    val speaking: Boolean get() = mode == PlaybackMode.SPEAKING
    val paused: Boolean get() = mode == PlaybackMode.PAUSED
}

class PasteReaderViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private object Keys {
        const val TEXT = "playback_text"
        const val OFFSET = "playback_offset"
        const val RATE = "playback_rate"
        const val MODE = "playback_mode"
        const val SESSION = "playback_session"
        const val SELECTION_START = "selection_start"
        const val SELECTION_END = "selection_end"
    }

    private val playback = PlaybackSession(
        PlaybackSnapshot(
            text = savedState[Keys.TEXT] ?: "",
            offset = savedState[Keys.OFFSET] ?: 0,
            speechRate = savedState[Keys.RATE] ?: 1.2f,
            mode = runCatching {
                PlaybackMode.valueOf(savedState[Keys.MODE] ?: PlaybackMode.IDLE.name)
            }.getOrDefault(PlaybackMode.IDLE),
            sessionId = savedState[Keys.SESSION] ?: 0
        )
    )

    var uiState by mutableStateOf(
        PasteReaderUiState(
            text = playback.snapshot.text,
            selectionStart = (savedState[Keys.SELECTION_START] ?: playback.snapshot.offset)
                .coerceIn(0, playback.snapshot.text.length),
            selectionEnd = (savedState[Keys.SELECTION_END] ?: playback.snapshot.offset)
                .coerceIn(0, playback.snapshot.text.length),
            message = when (playback.snapshot.mode) {
                PlaybackMode.SPEAKING -> "Restaurando lectura…"
                PlaybackMode.PAUSED -> "Pausado"
                PlaybackMode.FINISHED -> "Lectura terminada."
                PlaybackMode.IDLE -> DEFAULT_MESSAGE
            },
            mode = playback.snapshot.mode,
            speechRate = playback.snapshot.speechRate
        )
    )
        private set

    private var tts: TextToSpeech? = null
    private var activeChunk: SpeechChunk? = null

    init {
        createTtsEngine()
        persistPlayback()
    }

    fun onEditorValueChange(text: String, selectionStart: Int, selectionEnd: Int) {
        if (text != playback.snapshot.text) {
            invalidateEngineQueue()
            playback.updateText(text)
            activeChunk = null
            uiState = uiState.copy(
                text = text,
                message = if (text.isBlank()) DEFAULT_MESSAGE else uiState.message,
                mode = playback.snapshot.mode,
                highlightStart = null,
                highlightEnd = null
            )
            persistPlayback()
        }
        updateSelection(selectionStart, selectionEnd)
    }

    fun pasteText(text: String) {
        invalidateEngineQueue()
        playback.stop()
        playback.updateText(text)
        activeChunk = null
        uiState = uiState.copy(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            message = "Texto pegado.",
            mode = playback.snapshot.mode,
            highlightStart = null,
            highlightEnd = null
        )
        persistAll()
    }

    fun clearText() {
        invalidateEngineQueue()
        playback.updateText("")
        activeChunk = null
        uiState = PasteReaderUiState(ttsReady = uiState.ttsReady, message = "Texto borrado.", speechRate = playback.snapshot.speechRate)
        persistAll()
    }

    fun onClipboardEmpty() {
        uiState = uiState.copy(message = "El portapapeles está vacío.")
    }

    fun startSpeech() {
        if (!uiState.ttsReady) {
            uiState = uiState.copy(message = "El TTS español de Android aún no está listo.")
            return
        }
        if (!playback.start()) {
            uiState = uiState.copy(message = "No hay texto para leer.")
            return
        }
        invalidateEngineQueue(stopFirst = true)
        syncMode()
        persistPlayback()
        speakFromCurrentOffset()
    }

    fun pauseSpeech() {
        if (!playback.pause()) return
        tts?.stop()
        activeChunk = null
        syncMode(message = "Pausado")
        persistPlayback()
    }

    fun resumeSpeech() {
        if (!uiState.ttsReady) {
            uiState = uiState.copy(message = "El TTS español de Android aún no está listo.")
            return
        }
        if (!playback.resume()) return
        tts?.stop()
        activeChunk = null
        syncMode()
        persistPlayback()
        speakFromCurrentOffset()
    }

    fun stopSpeech() {
        playback.stop()
        tts?.stop()
        activeChunk = null
        uiState = uiState.copy(
            selectionStart = 0,
            selectionEnd = 0,
            message = "Detenido.",
            mode = playback.snapshot.mode,
            highlightStart = null,
            highlightEnd = null
        )
        persistAll()
    }

    fun changeSpeechRate(rate: Float) {
        playback.updateSpeechRate(rate)
        tts?.setSpeechRate(playback.snapshot.speechRate)
        uiState = uiState.copy(speechRate = playback.snapshot.speechRate)
        persistPlayback()
    }

    private fun createTtsEngine() {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(getApplication<Application>().applicationContext) { status ->
            onMain {
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = engine?.setLanguage(Locale("es", "ES"))
                    val ready = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                    uiState = uiState.copy(
                        ttsReady = ready,
                        message = if (ready) uiState.message else "El TTS español no está disponible en este móvil."
                    )
                    if (ready && playback.snapshot.mode == PlaybackMode.SPEAKING) {
                        playback.restartSpeakingSession()
                        syncMode()
                        persistPlayback()
                        speakFromCurrentOffset()
                    }
                } else {
                    uiState = uiState.copy(
                        ttsReady = false,
                        message = "No se pudo iniciar el TTS de Android."
                    )
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onMain {
                val callback = parseUtteranceId(utteranceId) ?: return@onMain
                val chunk = activeChunk ?: return@onMain
                if (!isCurrent(callback, chunk)) return@onMain
                applyRange(callback.sessionId, chunk.startOffset, chunk.startOffset, chunk.endOffset)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) = onMain {
                val callback = parseUtteranceId(utteranceId) ?: return@onMain
                val chunk = activeChunk ?: return@onMain
                if (!isCurrent(callback, chunk)) return@onMain
                val absoluteStart = (chunk.startOffset + start).coerceIn(chunk.startOffset, chunk.endOffset)
                val absoluteEnd = (chunk.startOffset + end).coerceIn(absoluteStart, chunk.endOffset)
                applyRange(callback.sessionId, absoluteStart, absoluteStart, absoluteEnd)
            }

            override fun onDone(utteranceId: String?) = onMain {
                val callback = parseUtteranceId(utteranceId) ?: return@onMain
                val chunk = activeChunk ?: return@onMain
                if (!isCurrent(callback, chunk)) return@onMain
                if (!playback.onChunkDone(callback.sessionId, chunk.endOffset)) return@onMain
                activeChunk = null
                persistPlayback()
                if (playback.snapshot.mode == PlaybackMode.FINISHED) {
                    uiState = uiState.copy(
                        selectionStart = 0,
                        selectionEnd = 0,
                        message = "Lectura terminada.",
                        mode = playback.snapshot.mode,
                        highlightStart = null,
                        highlightEnd = null
                    )
                    persistAll()
                } else {
                    speakFromCurrentOffset()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = handleError(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = handleError(utteranceId)
        })
        tts = engine
    }

    private fun handleError(utteranceId: String?) = onMain {
        val callback = parseUtteranceId(utteranceId) ?: return@onMain
        val chunk = activeChunk ?: return@onMain
        if (!isCurrent(callback, chunk)) return@onMain
        playback.pause()
        activeChunk = null
        syncMode(message = "Error durante la lectura. Pulsa Play para reanudar.")
        persistPlayback()
    }

    private fun speakFromCurrentOffset() {
        val snapshot = playback.snapshot
        val engine = tts ?: return
        val chunk = splitSpeechText(snapshot.text, snapshot.offset).firstOrNull()
        if (chunk == null) {
            playback.onChunkDone(snapshot.sessionId, snapshot.text.length)
            syncMode(message = "Lectura terminada.")
            persistPlayback()
            return
        }
        activeChunk = chunk
        uiState = uiState.copy(
            selectionStart = chunk.startOffset,
            selectionEnd = chunk.startOffset,
            message = "Leyendo",
            mode = snapshot.mode,
            highlightStart = chunk.startOffset,
            highlightEnd = chunk.endOffset
        )
        persistAll()
        engine.setSpeechRate(snapshot.speechRate)
        engine.speak(
            chunk.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId(snapshot.sessionId, chunk.startOffset)
        )
    }

    private fun applyRange(sessionId: Int, offset: Int, highlightStart: Int, highlightEnd: Int) {
        if (!playback.onRange(sessionId, offset)) return
        uiState = uiState.copy(
            selectionStart = highlightEnd,
            selectionEnd = highlightEnd,
            highlightStart = highlightStart,
            highlightEnd = highlightEnd
        )
        persistAll()
    }

    private fun isCurrent(callback: UtteranceRef, chunk: SpeechChunk): Boolean =
        callback.sessionId == playback.snapshot.sessionId &&
            callback.chunkStart == chunk.startOffset &&
            playback.snapshot.mode == PlaybackMode.SPEAKING

    private fun updateSelection(start: Int, end: Int) {
        val safeStart = start.coerceIn(0, uiState.text.length)
        val safeEnd = end.coerceIn(0, uiState.text.length)
        uiState = uiState.copy(selectionStart = safeStart, selectionEnd = safeEnd)
        savedState[Keys.SELECTION_START] = safeStart
        savedState[Keys.SELECTION_END] = safeEnd
    }

    private fun syncMode(message: String? = null) {
        uiState = uiState.copy(
            mode = playback.snapshot.mode,
            message = message ?: uiState.message,
            speechRate = playback.snapshot.speechRate,
            highlightStart = if (playback.snapshot.mode == PlaybackMode.SPEAKING) uiState.highlightStart else null,
            highlightEnd = if (playback.snapshot.mode == PlaybackMode.SPEAKING) uiState.highlightEnd else null
        )
    }

    private fun invalidateEngineQueue(stopFirst: Boolean = true) {
        if (stopFirst) tts?.stop()
        activeChunk = null
    }

    private fun persistPlayback() {
        val snapshot = playback.snapshot
        savedState[Keys.TEXT] = snapshot.text
        savedState[Keys.OFFSET] = snapshot.offset
        savedState[Keys.RATE] = snapshot.speechRate
        savedState[Keys.MODE] = snapshot.mode.name
        savedState[Keys.SESSION] = snapshot.sessionId
    }

    private fun persistAll() {
        persistPlayback()
        savedState[Keys.SELECTION_START] = uiState.selectionStart
        savedState[Keys.SELECTION_END] = uiState.selectionEnd
    }

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}

private data class UtteranceRef(val sessionId: Int, val chunkStart: Int)

private fun utteranceId(sessionId: Int, chunkStart: Int): String =
    "pastereader:$sessionId:$chunkStart"

private fun parseUtteranceId(value: String?): UtteranceRef? {
    val parts = value?.split(':') ?: return null
    if (parts.size != 3 || parts[0] != "pastereader") return null
    return UtteranceRef(
        sessionId = parts[1].toIntOrNull() ?: return null,
        chunkStart = parts[2].toIntOrNull() ?: return null
    )
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
