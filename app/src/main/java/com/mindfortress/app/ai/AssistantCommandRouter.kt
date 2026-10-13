package com.mindfortress.app.ai

import java.util.Locale

/**
 * Deterministic, testable intent parser for common phone actions.
 *
 * This is deliberately a tool router, not a pretend general LLM: unrecognized requests
 * fall through to the companion's reasoning engine. High-impact actions only open the
 * Android app's own confirmation/compose UI; this router never places calls or sends SMS.
 */
sealed interface AssistantCommand {
    data class OpenApp(val name: String) : AssistantCommand
    data class OpenWebsite(val url: String) : AssistantCommand
    data class SearchWeb(val query: String) : AssistantCommand
    data class ResearchOnline(val query: String) : AssistantCommand
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : AssistantCommand
    data class SetTimer(val durationSeconds: Int, val label: String) : AssistantCommand
    data class EngageFocusMode(val minutes: Int) : AssistantCommand
    data object ClearDistractions : AssistantCommand
    data class SetVolume(val percent: Int) : AssistantCommand
    data class DialNumber(val number: String) : AssistantCommand
    data class ComposeSms(val number: String, val body: String) : AssistantCommand
    data class CreateReminder(val delaySeconds: Int, val message: String) : AssistantCommand
    data class NavigateTo(val destination: String) : AssistantCommand
    data class OpenSettings(val section: String) : AssistantCommand
    data class Remember(val text: String) : AssistantCommand
    data object ListMemories : AssistantCommand
    data object ListNotifications : AssistantCommand
    data object SummarizeNotifications : AssistantCommand
    data object ClearNotificationHistory : AssistantCommand
    data object GetCurrentTime : AssistantCommand
    data object GetBatteryStatus : AssistantCommand
    data object GetVolumeStatus : AssistantCommand
    data class ForgetMemory(val query: String) : AssistantCommand
    data class AddTask(val text: String) : AssistantCommand
    data object ListTasks : AssistantCommand
    data class CompleteTask(val query: String) : AssistantCommand
    data object Help : AssistantCommand
}

object AssistantCommandRouter {
    private val websitePattern = Regex(
        """^(?:open|visit|go to)\s+(https?://\S+|www\.[^\s]+|[a-z0-9][a-z0-9.-]*\.[a-z]{2,}(?:/\S*)?)$""",
        RegexOption.IGNORE_CASE
    )
    private val alarmPattern = Regex(
        """^(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for|at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?(?:\s+(?:called|named)\s+(.+))?$""",
        RegexOption.IGNORE_CASE
    )
    private val wakePattern = Regex(
        """^wake me(?: up)? at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""",
        RegexOption.IGNORE_CASE
    )
    private val timerPattern = Regex(
        """^(?:(?:set|start|create)\s+)?(?:a\s+)?timer\s+(?:for\s+)?(\d{1,5})\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)(?:\s+(?:called|named|for)\s+(.+))?$""",
        RegexOption.IGNORE_CASE
    )
    private val reminderDurationPattern = Regex(
        """^remind me in\s+(\d{1,5})\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\s+(?:to\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val focusPattern = Regex(
        """^(?:(?:start|enable|activate)\s+)?(?:deep focus|focus lock|monk mode|padhai shuru karo|study mode)(?:\s+(?:for\s+)?(\d{1,4})\s*(minutes?|mins?|hours?|hrs?))?$""",
        RegexOption.IGNORE_CASE
    )
    private val legacyFocusPattern = Regex(
        """^lock (?:(?:my|the)\s+)?phone(?:\s+for\s+(\d{1,4})\s*(minutes?|mins?|hours?|hrs?))?$""",
        RegexOption.IGNORE_CASE
    )
    private val clearDistractionsPattern = Regex(
        """^(?:clear phone|clear distractions|block everything|purge distractions|stop distractions|distraction roko|distraction band karo)$""",
        RegexOption.IGNORE_CASE
    )
    private val volumePattern = Regex(
        """^(?:set )?volume (?:to )?(\d{1,3})\s*%?$""",
        RegexOption.IGNORE_CASE
    )
    private val dialPattern = Regex(
        """^(?:call|dial|phone)\s+([+()\d][+()\d .-]{5,})$""",
        RegexOption.IGNORE_CASE
    )
    private val smsPattern = Regex(
        """^(?:text|sms|message)\s+(\+?\d[\d ()-]{5,})\s+(?:saying|that|with message|message)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val rememberPattern = Regex(
        """^(?:remember(?: that)?|save (?:a )?note(?: that)?|make (?:a )?note(?: that)?|note that)[: ,]*\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val addTaskPattern = Regex(
        """^(?:add (?:a )?task|create (?:a )?task|to-?do)[: ,]*\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val completeTaskPattern = Regex(
        """^(?:complete|finish|mark done|check off) (?:the )?task[: ]+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): AssistantCommand? {
        val text = normalize(input)
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT)

        if (lower in setOf("what time is it", "tell me the time", "current time", "time now", "what is the time")) {
            return AssistantCommand.GetCurrentTime
        }
        if (lower in setOf("battery status", "what is my battery", "what's my battery", "battery percentage", "how much battery is left", "check battery")) {
            return AssistantCommand.GetBatteryStatus
        }
        if (lower in setOf("volume status", "what is the volume", "current volume", "how loud is media volume")) {
            return AssistantCommand.GetVolumeStatus
        }
        if (lower in setOf("read notifications", "read my notifications", "show notifications", "list notifications", "what did i miss", "what notifications did i get")) {
            return AssistantCommand.ListNotifications
        }
        if (lower in setOf("summarize notifications", "summarize my notifications", "summarise my notifications", "notification summary")) {
            return AssistantCommand.SummarizeNotifications
        }
        if (lower in setOf("clear notification history", "forget notification history", "delete captured notifications")) {
            return AssistantCommand.ClearNotificationHistory
        }
        if (lower in setOf("help", "what can you do", "what can jarvis do", "show commands", "list commands")) {
            return AssistantCommand.Help
        }
        if (lower in setOf("show tasks", "list tasks", "my tasks", "what are my tasks", "show my tasks", "list my tasks")) {
            return AssistantCommand.ListTasks
        }
        if (lower in setOf("show memories", "list memories", "what do you remember", "what do you remember about me", "show my notes", "list notes")) {
            return AssistantCommand.ListMemories
        }
        if (lower in setOf("open settings", "system settings", "phone settings", "settings")) {
            return AssistantCommand.OpenSettings("general")
        }

        if (clearDistractionsPattern.matches(text)) return AssistantCommand.ClearDistractions

        val focusMatch = focusPattern.find(text) ?: legacyFocusPattern.find(text)
        if (focusMatch != null) {
            val amount = focusMatch.groupValues.getOrNull(1).orEmpty().toIntOrNull()
            val unit = focusMatch.groupValues.getOrNull(2).orEmpty().lowercase(Locale.ROOT)
            val minutes = when {
                amount == null -> 60
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * 60
                else -> amount
            }
            if (minutes !in 1..1440) return null
            return AssistantCommand.EngageFocusMode(minutes)
        }

        volumePattern.find(text)?.let { match ->
            val percent = match.groupValues[1].toIntOrNull() ?: return null
            if (percent !in 0..100) return null
            return AssistantCommand.SetVolume(percent)
        }

        val settingsMatch = Regex(
            """^(?:open|show|go to)\s+(?:the\s+)?(wi-?fi|wireless|bluetooth|sound|volume|display|screen|location|battery|battery saver|accessibility|captions|notification access|notifications|app settings|installed apps|security|privacy)\s+settings$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (settingsMatch != null) {
            val section = settingsMatch.groupValues[1].lowercase(Locale.ROOT)
            return AssistantCommand.OpenSettings(
                when (section) {
                    "wi-fi", "wifi", "wireless" -> "wifi"
                    "bluetooth" -> "bluetooth"
                    "sound", "volume" -> "sound"
                    "display", "screen" -> "display"
                    "location" -> "location"
                    "battery", "battery saver" -> "battery"
                    "accessibility" -> "accessibility"
                    "captions" -> "captions"
                    "notification access", "notifications" -> "notifications"
                    "app settings", "installed apps" -> "apps"
                    "security", "privacy" -> "security"
                    else -> "general"
                }
            )
        }

        rememberPattern.find(text)?.let { return AssistantCommand.Remember(it.groupValues[1].trim()) }
        addTaskPattern.find(text)?.let { return AssistantCommand.AddTask(it.groupValues[1].trim()) }
        completeTaskPattern.find(text)?.let { return AssistantCommand.CompleteTask(it.groupValues[1].trim()) }

        Regex("""^(?:forget|delete|remove) (?:the )?(?:memory|note)(?: about)?\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AssistantCommand.ForgetMemory(it.groupValues[1].trim()) }

        reminderDurationPattern.find(text)?.let { match ->
            val seconds = durationSeconds(match.groupValues[1], match.groupValues[2]) ?: return null
            return AssistantCommand.CreateReminder(seconds, match.groupValues[3].trim())
        }

        val alarmMatch = alarmPattern.find(text) ?: wakePattern.find(text)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = alarmMatch.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
            val amPm = alarmMatch.groupValues[3].lowercase(Locale.ROOT)
            if (hour !in 0..23 || minute !in 0..59) return null
            if (amPm == "am" || amPm == "pm") {
                if (hour !in 1..12) return null
                hour %= 12
                if (amPm == "pm") hour += 12
            }
            val label = alarmMatch.groupValues.getOrNull(4).orEmpty().ifBlank { "JARVIS alarm" }
            return AssistantCommand.SetAlarm(hour, minute, label)
        }

        timerPattern.find(text)?.let { match ->
            val seconds = durationSeconds(match.groupValues[1], match.groupValues[2]) ?: return null
            if (seconds <= 0) return null
            return AssistantCommand.SetTimer(seconds, match.groupValues.getOrNull(3).orEmpty().ifBlank { "JARVIS timer" })
        }

        smsPattern.find(text)?.let { match ->
            val number = normalizePhone(match.groupValues[1])
            val body = match.groupValues[2].trim()
            if (number.count(Char::isDigit) >= 7 && body.isNotBlank()) {
                return AssistantCommand.ComposeSms(number, body)
            }
        }

        dialPattern.find(text)?.let { match ->
            val number = normalizePhone(match.groupValues[1])
            if (number.count(Char::isDigit) >= 7) return AssistantCommand.DialNumber(number)
        }

        Regex("""^(?:navigate to|directions to|route to|take me to|find route to)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AssistantCommand.NavigateTo(it.groupValues[1].trim()) }

        Regex("""^(?:research online|research and summarize online|find academic papers on|find studies on|literature review on)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AssistantCommand.ResearchOnline(it.groupValues[1].trim()) }

        Regex("""^(?:search(?: the)?(?: web| internet)?(?: for)?|google|look up online|find online)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AssistantCommand.SearchWeb(it.groupValues[1].trim()) }

        websitePattern.find(text)?.let {
            var url = it.groupValues[1].trim().trimEnd('.', ',', '!', '?')
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "https://$url"
            return AssistantCommand.OpenWebsite(url)
        }

        Regex("""^(?:open|launch)\s+(?:the\s+)?(?:app\s+)?(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { match ->
                val name = match.groupValues[1].trim()
                if (name.isNotBlank() && name != "settings") return AssistantCommand.OpenApp(name)
            }

        return null
    }

    private fun normalize(input: String): String = input.trim()
        .replace(Regex("""^(?:(?:hey|hi|okay|ok)\s+)?jarvis[,: ]+""", RegexOption.IGNORE_CASE), "")
        .trim()
        .trimEnd('.', '!', '?')

    private fun durationSeconds(value: String, unit: String): Int? {
        val amount = value.toLongOrNull() ?: return null
        val multiplier = when {
            unit.startsWith("hour") || unit.startsWith("hr") -> 3600L
            unit.startsWith("min") -> 60L
            else -> 1L
        }
        val seconds = amount * multiplier
        return seconds.takeIf { it in 1L..604_800L }?.toInt()
    }

    private fun normalizePhone(raw: String): String =
        raw.filterIndexed { i, ch -> ch.isDigit() || (ch == '+' && i == 0) }
}
