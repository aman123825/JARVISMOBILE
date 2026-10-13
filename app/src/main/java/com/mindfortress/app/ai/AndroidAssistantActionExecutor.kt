package com.mindfortress.app.ai

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.mindfortress.app.ReminderReceiver

data class AssistantToolResult(val reply: String, val speech: String = reply, val actionTag: String)

/**
 * Executes typed intents only. Calls open the dialer, SMS actions open a draft,
 * and alarm/timer actions leave the Clock UI available to review.
 */
class AndroidAssistantActionExecutor(
    private val context: Context,
    private val memory: AssistantMemoryStore = AssistantMemoryStore(context)
) {
    fun execute(command: AssistantCommand): AssistantToolResult = when (command) {
        is AssistantCommand.OpenApp -> openApp(command.name)
        is AssistantCommand.OpenWebsite -> launch(Intent(Intent.ACTION_VIEW, Uri.parse(command.url)), "Opening ${command.url}.", "OPEN_WEBSITE")
        is AssistantCommand.SearchWeb -> launch(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(command.query)}")),
            "Searching the web for ${command.query}.", "WEB_SEARCH"
        )
        is AssistantCommand.SetAlarm -> launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, command.label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            "Opening Clock to set an alarm for %02d:%02d. Check and confirm it in Clock.".format(command.hour, command.minute),
            "SET_ALARM"
        )
        is AssistantCommand.SetTimer -> launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, command.durationSeconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, command.label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            "Opening Clock to set a timer for ${formatDuration(command.durationSeconds)}. Confirm it in Clock.",
            "SET_TIMER"
        )
        is AssistantCommand.DialNumber -> launch(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${command.number}")),
            "Opening the dialer for ${command.number}. I will not place the call automatically.",
            "DIAL_NUMBER"
        )
        is AssistantCommand.ComposeSms -> launch(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${command.number}"))
                .putExtra("sms_body", command.body),
            "Opening a message draft to ${command.number}. Review it and tap Send yourself.",
            "COMPOSE_SMS"
        )
        is AssistantCommand.CreateReminder -> createReminder(command)
        is AssistantCommand.NavigateTo -> launch(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(command.destination)}")),
            "Opening maps for ${command.destination}.", "NAVIGATE"
        )
        is AssistantCommand.OpenSettings -> openSettings(command.section)
        is AssistantCommand.Remember -> {
            if (memory.remember(command.text)) result("Saved to JARVIS's private on-device memory: \"${command.text}\"", "MEMORY_SAVED")
            else result("Tell me what you want me to remember.", "MEMORY_EMPTY")
        }
        AssistantCommand.ListMemories -> {
            val items = memory.memories()
            if (items.isEmpty()) result("Your JARVIS memory is empty. Say 'remember that …' to save a detail on this device.", "MEMORY_EMPTY")
            else result("What I remember:\n" + items.mapIndexed { i, v -> "${i + 1}. $v" }.joinToString("\n"), "MEMORY_LIST")
        }
        is AssistantCommand.ForgetMemory -> {
            val removed = memory.forget(command.query)
            if (removed > 0) result("Removed $removed matching memory item(s) from this device.", "MEMORY_FORGOTTEN")
            else result("I couldn't find a saved memory containing \"${command.query}\".", "MEMORY_NOT_FOUND")
        }
        is AssistantCommand.AddTask -> {
            val task = memory.addTask(command.text)
            if (task != null) result("Added task: \"${task.title}\". Say 'show tasks' to review your list.", "TASK_ADDED")
            else result("Tell me what task to add.", "TASK_EMPTY")
        }
        AssistantCommand.ListTasks -> {
            val tasks = memory.tasks()
            if (tasks.isEmpty()) result("You don't have any saved tasks yet. Say 'add task …' to create one.", "TASK_LIST_EMPTY")
            else result("Your tasks:\n" + tasks.mapIndexed { i, t -> "${i + 1}. ${if (t.completed) "[x]" else "[ ]"} ${t.title}" }.joinToString("\n"), "TASK_LIST")
        }
        is AssistantCommand.CompleteTask -> {
            val task = memory.completeTask(command.query)
            if (task != null) result("Marked task complete: \"${task.title}\"", "TASK_COMPLETED")
            else result("I couldn't find an open task matching \"${command.query}\".", "TASK_NOT_FOUND")
        }
        is AssistantCommand.ResearchOnline -> result(
            "Online research uses source retrieval and is handled by the research pipeline.",
            "RESEARCH_ROUTE"
        )
        AssistantCommand.Help -> help()
    }

    private fun openApp(rawName: String): AssistantToolResult {
        val name = rawName.trim()
        val packages = mapOf(
            "youtube" to "com.google.android.youtube", "yt" to "com.google.android.youtube",
            "chrome" to "com.android.chrome", "browser" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox", "spotify" to "com.spotify.music",
            "whatsapp" to "com.whatsapp", "camera" to "com.sec.android.app.camera",
            "calculator" to "com.sec.android.app.popupcalculator", "clock" to "com.sec.android.app.clockpackage",
            "calendar" to "com.samsung.android.calendar", "gallery" to "com.sec.android.gallery3d",
            "maps" to "com.google.android.apps.maps", "gmail" to "com.google.android.gm",
            "play store" to "com.android.vending"
        )
        return try {
            val known = packages[name.lowercase()]?.let { context.packageManager.getLaunchIntentForPackage(it) }
            if (known != null) {
                known.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(known)
                result("Opening $name.", "OPEN_APP")
            } else {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = context.packageManager.queryIntentActivities(launcher, 0)
                val match = apps.firstOrNull { it.loadLabel(context.packageManager).toString().equals(name, true) }
                    ?: apps.firstOrNull { it.loadLabel(context.packageManager).toString().contains(name, true) }
                val info = match?.activityInfo
                if (info == null) result("I couldn't find an installed app named $name. Try its exact home-screen name.", "APP_NOT_FOUND")
                else {
                    context.startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(info.packageName, info.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    result("Opening ${info.loadLabel(context.packageManager)}.", "OPEN_APP")
                }
            }
        } catch (e: Exception) {
            result("I couldn't open $name: ${e.message ?: "Android rejected the launch"}.", "OPEN_APP_FAILED")
        }
    }

    private fun openSettings(section: String): AssistantToolResult {
        val action = when (section) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "captions" -> Settings.ACTION_CAPTIONING_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return launch(Intent(action), "Opening ${section.replaceFirstChar { it.uppercase() }} settings.", "OPEN_SETTINGS")
    }

    private fun createReminder(command: AssistantCommand.CreateReminder): AssistantToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return result("Notifications are not allowed for JARVIS, so I did not schedule this reminder. Enable JARVIS notifications in Android Settings and repeat the request.", "REMINDER_PERMISSION_REQUIRED")
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return result("Android's alarm service isn't available.", "REMINDER_FAILED")
        val id = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMINDER)
                .putExtra(ReminderReceiver.EXTRA_MESSAGE, command.message)
                .putExtra(ReminderReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + command.delaySeconds * 1000L,
                pendingIntent
            )
            result("Reminder scheduled in about ${formatDuration(command.delaySeconds)}: \"${command.message}\". Android may delay it under battery restrictions.", "CREATE_REMINDER")
        } catch (e: Exception) {
            result("I couldn't schedule the reminder: ${e.message ?: "Android rejected the alarm"}.", "REMINDER_FAILED")
        }
    }

    private fun help() = result(
        """JARVIS can execute these local commands:
Open/launch an installed app; search the web; open a website; navigate to a place;
set an alarm or timer; call a number by opening the dialer; draft an SMS for your review;
open Wi-Fi, Bluetooth, sound, display, location, battery, accessibility or caption settings;
create local reminders; remember and forget notes; add/list/complete tasks.
For other requests, the local rule/knowledge engine responds when it recognizes the topic.""".trimIndent(),
        "COMMAND_HELP"
    )

    private fun launch(intent: Intent, message: String, tag: String): AssistantToolResult = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        result(message, tag)
    } catch (e: ActivityNotFoundException) {
        result("No app is available for that action on this device.", "${tag}_UNAVAILABLE")
    } catch (e: Exception) {
        Log.w("AssistantActions", "Action $tag failed", e)
        result("That action failed: ${e.message ?: "Android rejected it"}.", "${tag}_FAILED")
    }

    private fun result(reply: String, tag: String) =
        AssistantToolResult(reply, reply.replace(Regex("[#*\\[\\]]"), ""), tag)

    private fun formatDuration(seconds: Int): String = when {
        seconds >= 3600 && seconds % 3600 == 0 -> "${seconds / 3600} hour(s)"
        seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60} minute(s)"
        else -> "$seconds second(s)"
    }
}
