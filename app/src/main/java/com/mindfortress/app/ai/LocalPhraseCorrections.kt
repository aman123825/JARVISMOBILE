package com.mindfortress.app.ai

import android.content.Context
import org.json.JSONObject

/**
 * Small, explicitly user-authored transcription correction dictionary.
 * It stores only phrase -> phrase mappings in private app SharedPreferences.
 * No audio, transcripts, account data, or network requests are stored here.
 */
class LocalPhraseCorrections(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "jarvis_local_phrase_corrections",
        Context.MODE_PRIVATE
    )

    fun apply(text: String): String {
        if (text.isBlank()) return text
        val mappings = readMappings()
        var corrected = text
        mappings.entries
            .sortedByDescending { it.key.length }
            .forEach { (source, replacement) ->
                corrected = corrected.replace(
                    Regex(Regex.escape(source), RegexOption.IGNORE_CASE),
                    replacement
                )
            }
        return corrected
    }

    fun save(sourcePhrase: String, correctedPhrase: String): Boolean {
        val source = sourcePhrase.trim().take(MAX_PHRASE_LENGTH)
        val replacement = correctedPhrase.trim().take(MAX_PHRASE_LENGTH)
        if (source.isBlank() || replacement.isBlank() || source.equals(replacement, true)) {
            return false
        }

        val json = JSONObject()
        readMappings().forEach { (key, value) -> json.put(key, value) }
        json.put(source, replacement)
        preferences.edit().putString(KEY_MAPPINGS, json.toString()).apply()
        return true
    }

    fun remove(sourcePhrase: String) {
        val json = JSONObject()
        readMappings()
            .filterKeys { !it.equals(sourcePhrase.trim(), ignoreCase = true) }
            .forEach { (key, value) -> json.put(key, value) }
        preferences.edit().putString(KEY_MAPPINGS, json.toString()).apply()
    }

    fun all(): Map<String, String> = readMappings()

    private fun readMappings(): Map<String, String> {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_MAPPINGS, "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        val result = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key).trim()
            if (key.isNotBlank() && value.isNotBlank()) result[key] = value
        }
        return result
    }

    companion object {
        private const val KEY_MAPPINGS = "mappings"
        private const val MAX_PHRASE_LENGTH = 160
    }
}
