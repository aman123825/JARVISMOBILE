package com.mindfortress.app.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class AssistantTask(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAtMillis: Long
)

/**
 * Small on-device memory for explicit user requests only.
 * Data is private to this app and never synced or sent to a model provider.
 */
class AssistantMemoryStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "jarvis_assistant_memory",
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    fun remember(text: String): Boolean = synchronized(lock) {
        val value = text.trim().take(MAX_ENTRY_LENGTH)
        if (value.isBlank()) return false
        val entries = readStrings(KEY_MEMORIES)
        if (entries.any { it.equals(value, ignoreCase = true) }) return true
        writeStrings(KEY_MEMORIES, (entries + value).takeLast(MAX_ENTRIES))
        true
    }

    fun memories(): List<String> = synchronized(lock) { readStrings(KEY_MEMORIES) }

    fun forget(query: String): Int = synchronized(lock) {
        val before = readStrings(KEY_MEMORIES)
        val needle = query.trim()
        if (needle.isBlank()) return 0
        val after = before.filterNot { it.contains(needle, ignoreCase = true) }
        writeStrings(KEY_MEMORIES, after)
        before.size - after.size
    }

    fun addTask(title: String): AssistantTask? = synchronized(lock) {
        val text = title.trim().take(MAX_ENTRY_LENGTH)
        if (text.isBlank()) return null
        val task = AssistantTask(
            id = "${System.currentTimeMillis()}-${System.nanoTime()}",
            title = text,
            completed = false,
            createdAtMillis = System.currentTimeMillis()
        )
        val tasks = readTasks().toMutableList()
        tasks.add(task)
        writeTasks(tasks.takeLast(MAX_ENTRIES))
        task
    }

    fun tasks(): List<AssistantTask> = synchronized(lock) { readTasks() }

    fun completeTask(query: String): AssistantTask? = synchronized(lock) {
        val needle = query.trim()
        if (needle.isBlank()) return null
        val tasks = readTasks().toMutableList()
        val index = tasks.indexOfFirst {
            !it.completed && (it.id == needle || it.title.contains(needle, ignoreCase = true))
        }
        if (index < 0) return null
        val done = tasks[index].copy(completed = true)
        tasks[index] = done
        writeTasks(tasks)
        done
    }

    private fun readStrings(key: String): List<String> {
        val array = runCatching { JSONArray(preferences.getString(key, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    private fun writeStrings(key: String, values: List<String>) {
        val array = JSONArray()
        values.takeLast(MAX_ENTRIES).forEach { value -> array.put(value) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun readTasks(): List<AssistantTask> {
        val array = runCatching { JSONArray(preferences.getString(KEY_TASKS, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val title = obj.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            AssistantTask(
                id = id,
                title = title,
                completed = obj.optBoolean("completed", false),
                createdAtMillis = obj.optLong("createdAt", 0L)
            )
        }
    }

    private fun writeTasks(tasks: List<AssistantTask>) {
        val array = JSONArray()
        tasks.takeLast(MAX_ENTRIES).forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("completed", task.completed)
                    .put("createdAt", task.createdAtMillis)
            )
        }
        preferences.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    companion object {
        private const val KEY_MEMORIES = "memories"
        private const val KEY_TASKS = "tasks"
        private const val MAX_ENTRY_LENGTH = 500
        private const val MAX_ENTRIES = 100
    }
}
