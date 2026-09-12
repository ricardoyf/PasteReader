package com.ricardo.txtreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionTest {
    private val text = "uno dos tres cuatro cinco"

    @Test
    fun speakingRotation_keepsModeTextRateAndCurrentOffset() {
        val original = PlaybackSession(PlaybackSnapshot(text = text, speechRate = 1.4f))
        assertTrue(original.start())
        val session = original.snapshot.sessionId
        assertTrue(original.onRange(session, 8))

        val afterRotation = PlaybackSession(original.snapshot)

        assertEquals(PlaybackMode.SPEAKING, afterRotation.snapshot.mode)
        assertEquals(8, afterRotation.snapshot.offset)
        assertEquals(1.4f, afterRotation.snapshot.speechRate)
        assertEquals(text, afterRotation.snapshot.text)
    }

    @Test
    fun pausedRotationThenPlay_resumesFromCurrentOffset() {
        val original = PlaybackSession(PlaybackSnapshot(text = text))
        original.start()
        original.onRange(original.snapshot.sessionId, 12)
        assertTrue(original.pause())

        val afterRotation = PlaybackSession(original.snapshot)
        assertEquals(PlaybackMode.PAUSED, afterRotation.snapshot.mode)
        assertEquals(12, afterRotation.snapshot.offset)

        assertTrue(afterRotation.resume())
        assertEquals(PlaybackMode.SPEAKING, afterRotation.snapshot.mode)
        assertEquals(12, afterRotation.snapshot.offset)
    }

    @Test
    fun staleCallbacks_doNotChangeOffsetOrFinishCurrentSession() {
        val playback = PlaybackSession(PlaybackSnapshot(text = text))
        playback.start()
        val staleSession = playback.snapshot.sessionId
        playback.pause()
        playback.resume()
        val currentSession = playback.snapshot.sessionId

        assertFalse(playback.onRange(staleSession, 18))
        assertFalse(playback.onChunkDone(staleSession, text.length))
        assertEquals(currentSession, playback.snapshot.sessionId)
        assertEquals(0, playback.snapshot.offset)
        assertEquals(PlaybackMode.SPEAKING, playback.snapshot.mode)
    }

    @Test
    fun stop_resetsToBeginningAndInvalidatesCallbacks() {
        val playback = PlaybackSession(PlaybackSnapshot(text = text))
        playback.start()
        val oldSession = playback.snapshot.sessionId
        playback.onRange(oldSession, 15)

        playback.stop()

        assertEquals(PlaybackMode.IDLE, playback.snapshot.mode)
        assertEquals(0, playback.snapshot.offset)
        assertFalse(playback.onRange(oldSession, 20))
    }

    @Test
    fun finish_resetsToBeginningAndNextPlayStartsAtBeginning() {
        val playback = PlaybackSession(PlaybackSnapshot(text = text))
        playback.start()
        val session = playback.snapshot.sessionId

        assertTrue(playback.onChunkDone(session, text.length))
        assertEquals(PlaybackMode.FINISHED, playback.snapshot.mode)
        assertEquals(0, playback.snapshot.offset)

        assertTrue(playback.start())
        assertEquals(PlaybackMode.SPEAKING, playback.snapshot.mode)
        assertEquals(0, playback.snapshot.offset)
    }

    @Test
    fun restoredSpeakingSession_keepsOffsetAndInvalidatesOldCallbacks() {
        val playback = PlaybackSession(
            PlaybackSnapshot(
                text = text,
                offset = 9,
                mode = PlaybackMode.SPEAKING,
                sessionId = 4
            )
        )

        assertTrue(playback.restartSpeakingSession())

        assertEquals(9, playback.snapshot.offset)
        assertEquals(5, playback.snapshot.sessionId)
        assertFalse(playback.onRange(4, 20))
    }
}
