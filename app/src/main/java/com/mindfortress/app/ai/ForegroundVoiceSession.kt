package com.mindfortress.app.ai

/**
 * Resolves ownership when a foreground Compose screen temporarily takes over from
 * the background hands-free listener. The most recently visible UI session wins.
 */
object ForegroundVoiceSession {
    private val monitor = Any()
    private var nextToken = 0L
    private var activeToken: Long? = null

    fun claimUiSession(): Long = synchronized(monitor) {
        nextToken += 1
        activeToken = nextToken
        nextToken
    }

    /** Returns true only for the latest foreground session, so stale screens cannot restart audio. */
    fun releaseUiSession(token: Long): Boolean = synchronized(monitor) {
        if (activeToken == token) {
            activeToken = null
            true
        } else {
            false
        }
    }

    fun hasActiveUiSession(): Boolean = synchronized(monitor) {
        activeToken != null
    }
}
