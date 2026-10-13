package com.mindfortress.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCaptureCoordinationTest {

    @Test
    fun microphoneLease_allowsOnlyOneOwnerAtATime() {
        val first = Any()
        val second = Any()

        assertTrue(MicrophoneCaptureArbiter.tryAcquire(first))
        assertTrue(MicrophoneCaptureArbiter.isOwnedByAnother(second))
        assertFalse(MicrophoneCaptureArbiter.tryAcquire(second))

        MicrophoneCaptureArbiter.release(first)
        assertTrue(MicrophoneCaptureArbiter.tryAcquire(second))
        MicrophoneCaptureArbiter.release(second)
    }

    @Test
    fun microphoneLease_releaseFromNonOwnerDoesNotClearLease() {
        val owner = Any()
        val nonOwner = Any()

        assertTrue(MicrophoneCaptureArbiter.tryAcquire(owner))
        MicrophoneCaptureArbiter.release(nonOwner)

        assertTrue(MicrophoneCaptureArbiter.isOwnedByAnother(nonOwner))
        MicrophoneCaptureArbiter.release(owner)
    }

    @Test
    fun foregroundSession_staleUiCannotReleaseNewerOwner() {
        val older = ForegroundVoiceSession.claimUiSession()
        val newer = ForegroundVoiceSession.claimUiSession()

        assertFalse(ForegroundVoiceSession.releaseUiSession(older))
        assertTrue(ForegroundVoiceSession.hasActiveUiSession())
        assertTrue(ForegroundVoiceSession.releaseUiSession(newer))
        assertFalse(ForegroundVoiceSession.hasActiveUiSession())
    }
}
