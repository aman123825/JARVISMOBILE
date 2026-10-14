package com.mindfortress.app.ai

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.util.Locale

/**
 * A small, deterministic safety boundary between user text and phone actions.
 *
 * This class only proposes actions; it never executes them. The UI must obtain
 * explicit approval before dispatching a proposal.
 */
enum class DeviceActionType {
    OPEN_APP,
    DRAFT_SMS,
    CALENDAR_REMINDER,
    FOCUS_LOCK
}

data class DeviceActionProposal(
    val type: DeviceActionType,
    val originalCommand: String,
    val summary: String,
    val explanation: String,
    /** App name, SMS recipient, or calendar event title depending on [type]. */
    val target: String? = null,
    /** SMS body for [DeviceActionType.DRAFT_SMS]. */
    val messageBody: String? = null
)

object PermissionFirstActionBroker {
    private val reminderPattern = Regex(
        """^(?:please\\s+)?remind\\s+me\\s+to\\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val messagePatterns = listOf(
        Regex(
            """^(?:please\\s+)?(?:send|text|message)\\s+(.+?)\\s+(?:a\\s+)?(?:text|sms|message)\\s+(?:saying|that says|with message|with text)\\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """^(?:please\\s+)?(?:send|text|message)\\s+(?:a\\s+)?(?:text|sms|message)\\s+to\\s+(.+?)\\s+(?:saying|that says|with message|with text)\\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """^(?:please\\s+)?(?:send|text|message)\\s+(.+?)\\s*:\\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
    )

    private val openAppPattern = Regex(
        """^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?[.!?]*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parse only actions that the current Android app can safely preview and
     * route through approval. Non-action conversation returns null.
     */
    fun propose(rawCommand: String): DeviceActionProposal? {
        val command = rawCommand.trim()
        if (command.isEmpty()) return null

        // Match messages before general "open/start app" handling.
        for (pattern in messagePatterns) {
            val match = pattern.matchEntire(command) ?: continue
            val recipient = match.groupValues[1].trim().trimEnd('.', '!', '?')
            val body = match.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return DeviceActionProposal(
                    type = DeviceActionType.DRAFT_SMS,
                    originalCommand = command,
                    summary = "Prepare a text message to $recipient",
                    explanation = "After approval, the messaging app will open with the recipient and text filled in. Nothing is sent automatically; review it and press Send inside the messaging app.",
                    target = recipient,
                    messageBody = body
                )
            }
        }

        reminderPattern.matchEntire(command)?.let { match ->
            val title = match.groupValues[1].trim().take(160)
            if (title.isNotBlank()) {
                return DeviceActionProposal(
                    type = DeviceActionType.CALENDAR_REMINDER,
                    originalCommand = command,
                    summary = "Prepare a calendar reminder",
                    explanation = "After approval, Calendar will open an event draft titled below. Choose the date and time, then save it in Calendar. No reminder is saved by JARVIS at this stage.",
                    target = title
                )
            }
        }

        if (isFocusLockCommand(command)) {
            return DeviceActionProposal(
                type = DeviceActionType.FOCUS_LOCK,
                originalCommand = command,
                summary = "Enable Focus / Monk Mode",
                explanation = "This changes focus and blocking preferences and schedules an automatic unlock. Nothing changes unless you approve this action.",
            )
        }

        openAppPattern.matchEntire(command)?.let { match ->
            val appName = match.groupValues[1]
                .trim()
                .removeSuffix(" app")
                .trim()
            if (appName.isNotBlank()) {
                return DeviceActionProposal(
                    type = DeviceActionType.OPEN_APP,
                    originalCommand = command,
                    summary = "Open $appName",
                    explanation = "JARVIS will open this app on your phone only after you approve.",
                    target = appName
                )
            }
        }

        return null
    }

    /**
     * Keep this command matcher narrow. The previous broad "contains deep work"
     * check could activate phone-lock behavior for an informational question.
     */
    fun isFocusLockCommand(rawCommand: String): Boolean {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("""^(?:please\\s+)?lock(?:\\s+(?:(?:my|the)\\s+)?(?:phone|device))?(?:\\s+for\\s+\\d+\\s*(?:minutes?|mins?|hours?|hrs?))?[.!?]*$"""),
            Regex("""^(?:please\\s+)?padhai\\s+shuru(?:\\s+.*)?[.!?]*$"""),
            Regex("""^(?:please\\s+)?(?:activate|enable|start|begin|turn on)\\b.*\\b(?:monk mode|deep work|focus lock|focus mode|ultradian)\\b.*[.!?]*$"""),
            Regex("""^(?:please\\s+)?(?:monk mode|deep work|focus lock|focus mode)(?:\\s+for\\s+\\d+\\s*(?:minutes?|mins?|hours?|hrs?))?[.!?]*$""")
        )
        return patterns.any { it.matches(command) }
    }

    /** Opens the system SMS composer. It does not send the message. */
    fun createSmsDraftIntent(proposal: DeviceActionProposal): Intent {
        require(proposal.type == DeviceActionType.DRAFT_SMS)
        val recipient = requireNotNull(proposal.target) { "SMS recipient is missing." }
        val body = requireNotNull(proposal.messageBody) { "SMS body is missing." }
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(recipient)}")
            putExtra("sms_body", body)
        }
    }

    /**
     * Opens Calendar's event editor with a title. Android's Calendar app asks
     * the user to choose the date/time and save the event.
     */
    fun createCalendarDraftIntent(proposal: DeviceActionProposal): Intent {
        require(proposal.type == DeviceActionType.CALENDAR_REMINDER)
        val title = requireNotNull(proposal.target) { "Reminder title is missing." }
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
        }
    }
}
