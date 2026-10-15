package com.mindfortress.app.ai

/**
 * Process-wide exclusive lease for Android microphone capture.
 *
 * AudioRecord does not safely support multiple independent recognizers consuming the
 * same microphone in parallel. Every JARVIS-owned capture path should acquire this
 * lease before opening AudioRecord and release it when capture stops.
 */
object MicrophoneCaptureArbiter {
    private val monitor = Any()
    private var currentOwner: Any? = null

    fun tryAcquire(owner: Any): Boolean = synchronized(monitor) {
        val active = currentOwner
        if (active == null || active === owner) {
            currentOwner = owner
            true
        } else {
            false
        }
    }

    fun isOwnedByAnother(owner: Any): Boolean = synchronized(monitor) {
        currentOwner != null && currentOwner !== owner
    }

    fun release(owner: Any) {
        synchronized(monitor) {
            if (currentOwner === owner) currentOwner = null
        }
    }
}
