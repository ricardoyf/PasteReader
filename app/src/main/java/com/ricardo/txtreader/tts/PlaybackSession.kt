package com.ricardo.txtreader.tts

enum class PlaybackMode {
    IDLE,
    SPEAKING,
    PAUSED,
    FINISHED
}

data class PlaybackSnapshot(
    val text: String = "",
    val offset: Int = 0,
    val speechRate: Float = 1.2f,
    val mode: PlaybackMode = PlaybackMode.IDLE,
    val sessionId: Int = 0
)

/** Pure playback state. Android TTS side effects live in PasteReaderViewModel. */
class PlaybackSession(initial: PlaybackSnapshot = PlaybackSnapshot()) {
    var snapshot: PlaybackSnapshot = initial.normalized()
        private set

    fun updateText(text: String) {
        if (text == snapshot.text) return
        snapshot = snapshot.copy(
            text = text,
            offset = 0,
            mode = PlaybackMode.IDLE,
            sessionId = snapshot.sessionId + 1
        )
    }

    fun updateSpeechRate(rate: Float) {
        snapshot = snapshot.copy(speechRate = rate.coerceIn(0.6f, 1.6f))
    }

    fun start(): Boolean {
        if (snapshot.text.isBlank()) return false
        snapshot = snapshot.copy(
            offset = 0,
            mode = PlaybackMode.SPEAKING,
            sessionId = snapshot.sessionId + 1
        )
        return true
    }

    fun pause(): Boolean {
        if (snapshot.mode != PlaybackMode.SPEAKING) return false
        snapshot = snapshot.copy(
            mode = PlaybackMode.PAUSED,
            sessionId = snapshot.sessionId + 1
        )
        return true
    }

    fun resume(): Boolean {
        if (snapshot.text.isBlank() || snapshot.mode != PlaybackMode.PAUSED) return false
        snapshot = snapshot.copy(
            mode = PlaybackMode.SPEAKING,
            sessionId = snapshot.sessionId + 1
        )
        return true
    }

    /** Reissues speech after an engine/Activity restoration without changing the offset. */
    fun restartSpeakingSession(): Boolean {
        if (snapshot.text.isBlank() || snapshot.mode != PlaybackMode.SPEAKING) return false
        snapshot = snapshot.copy(sessionId = snapshot.sessionId + 1)
        return true
    }

    fun onRange(sessionId: Int, absoluteStart: Int): Boolean {
        if (snapshot.mode != PlaybackMode.SPEAKING || sessionId != snapshot.sessionId) return false
        snapshot = snapshot.copy(offset = absoluteStart.coerceIn(0, snapshot.text.length))
        return true
    }

    fun onChunkDone(sessionId: Int, chunkEnd: Int): Boolean {
        if (snapshot.mode != PlaybackMode.SPEAKING || sessionId != snapshot.sessionId) return false
        val nextOffset = chunkEnd.coerceIn(0, snapshot.text.length)
        snapshot = if (nextOffset >= snapshot.text.length) {
            snapshot.copy(offset = 0, mode = PlaybackMode.FINISHED)
        } else {
            snapshot.copy(offset = nextOffset)
        }
        return true
    }

    fun stop() {
        snapshot = snapshot.copy(
            offset = 0,
            mode = PlaybackMode.IDLE,
            sessionId = snapshot.sessionId + 1
        )
    }

    private fun PlaybackSnapshot.normalized(): PlaybackSnapshot = copy(
        offset = offset.coerceIn(0, text.length),
        speechRate = speechRate.coerceIn(0.6f, 1.6f),
        mode = if (text.isBlank()) PlaybackMode.IDLE else mode
    )
}
