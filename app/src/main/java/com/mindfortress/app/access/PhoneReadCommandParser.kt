package com.mindfortress.app.access

import java.util.Locale

enum class PhoneReadIntent {
    LATEST_WHATSAPP_MESSAGE,
    LATEST_NOTIFICATION,
    RECENT_NOTIFICATIONS,
    CURRENT_SCREEN
}

/**
 * Deterministic parser for read-only phone context requests.
 * It never performs device actions and never treats arbitrary chat text as a command.
 */
object PhoneReadCommandParser {
    fun parse(raw: String): PhoneReadIntent? {
        val text = raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[.!?]+$"), "")
            .replace(Regex("\\s+"), " ")
        if (text.isBlank()) return null

        val asksToRead = listOf(
            "read", "tell me", "show me", "what is", "what's",
            "what are", "what did", "summarize", "describe"
        ).any { text.contains(it) }

        val isWhatsApp = text.contains("whatsapp") || text.contains("whats app")
        val mentionsMessage = text.contains("message") || text.contains("notification")
        val mentionsLatest = listOf("latest", "last", "most recent", "newest", "just received").any {
            text.contains(it)
        }

        if (asksToRead && isWhatsApp && mentionsMessage && mentionsLatest) {
            return PhoneReadIntent.LATEST_WHATSAPP_MESSAGE
        }

        val screenRequest = text.contains("screen") &&
            asksToRead &&
            listOf("this screen", "the screen", "my screen", "current screen", "on screen").any {
                text.contains(it)
            }
        if (screenRequest) return PhoneReadIntent.CURRENT_SCREEN

        val asksForNotifications = text.contains("notification") &&
            asksToRead &&
            listOf("latest", "last", "recent", "my notifications", "all notifications",
                "notifications", "just received").any { text.contains(it) }
        if (asksForNotifications) {
            return if (mentionsLatest) PhoneReadIntent.LATEST_NOTIFICATION
            else PhoneReadIntent.RECENT_NOTIFICATIONS
        }
        return null
    }
}
