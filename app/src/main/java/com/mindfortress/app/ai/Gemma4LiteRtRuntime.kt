package com.mindfortress.app.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Offline Gemma 4 inference runtime.
 *
 * The user must import a merged/exported .litertlm model. A PEFT/LoRA adapter
 * (.safetensors) is not a runnable model and is deliberately rejected by importModel().
 *
 * This runtime never downloads models or makes network requests. It has no tool
 * execution interface: all phone actions remain behind PermissionFirstActionBroker.
 */
class Gemma4LiteRtRuntime private constructor(context: Context) {
    companion object {
        const val MODEL_FILE_NAME = "jarvis-gemma4-e2b.litertlm"
        private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        private const val STORAGE_RESERVE_BYTES = 256L * 1024L * 1024L
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val MAX_PROMPT_CHARACTERS = 8_000

        private const val SYSTEM_INSTRUCTION = """
You are JARVIS, a private, offline personal assistant. Be natural, clear, useful and truthful.
Use the current conversation context when answering. Explain STEM problems step by step when asked.
Never claim you have opened an app, sent a message, saved a reminder, changed a setting, or completed a phone action.
Phone actions are handled separately by the Android action broker; they may require user approval.
Do not emit executable instructions, shell commands, or tool calls to perform device actions.
When the user asks about current facts that you cannot know offline, say that you may not have current information.
"""

        @Volatile
        private var singleton: Gemma4LiteRtRuntime? = null

        fun getInstance(context: Context): Gemma4LiteRtRuntime =
            singleton ?: synchronized(this) {
                singleton ?: Gemma4LiteRtRuntime(context.applicationContext).also { singleton = it }
            }
    }

    private val appContext = context.applicationContext
    private val modelDirectory: File =
        appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
    private val modelFile = File(modelDirectory, MODEL_FILE_NAME)
    private val cacheDirectory = File(appContext.cacheDir, "litertlm")
    private val inferenceMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun isModelInstalled(): Boolean =
        modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES

    fun installedModelPath(): String? =
        modelFile.absolutePath.takeIf { isModelInstalled() }

    /**
     * Copies a user-selected exported model into app-private storage.
     * A temp file and backup keep the previous model recoverable if replacement fails.
     */
    suspend fun installModel(uri: Uri): File = withContext(Dispatchers.IO) {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("JARVIS could not create its private models folder.")
        }

        val metadata = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                name to size
            }
        }

        val displayName = metadata?.first ?: uri.lastPathSegment.orEmpty()
        val expectedSize = metadata?.second ?: -1L
        if (!displayName.endsWith(".litertlm", ignoreCase = true)) {
            throw IOException(
                "Select the final exported .litertlm model. adapter_model.safetensors and checkpoint ZIPs cannot run on Android."
            )
        }

        if (expectedSize > 0L) {
            val requiredBytes = expectedSize + STORAGE_RESERVE_BYTES
            if (StatFs(modelDirectory.absolutePath).availableBytes < requiredBytes) {
                throw IOException("Not enough free storage to import this model plus a safe working margin.")
            }
        }

        val temporaryFile = File(modelDirectory, "$MODEL_FILE_NAME.part")
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw IOException("JARVIS could not clear an earlier incomplete model import.")
        }

        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Android could not open the selected model file.")
            input.use { source ->
                FileOutputStream(temporaryFile).use { destination ->
                    source.copyTo(destination, COPY_BUFFER_BYTES)
                }
            }
        } catch (error: Exception) {
            temporaryFile.delete()
            throw error
        }

        if (temporaryFile.length() < MIN_MODEL_BYTES) {
            temporaryFile.delete()
            throw IOException(
                "The selected file is too small to be a Gemma 4 E2B model. Choose the exported .litertlm file, not the LoRA adapter."
            )
        }

        inferenceMutex.lock()
        try {
            closeEngineUnsafe()
            val backupFile = File(modelDirectory, "$MODEL_FILE_NAME.previous")
            if (backupFile.exists()) backupFile.delete()

            val hadPreviousModel = modelFile.exists()
            if (hadPreviousModel && !modelFile.renameTo(backupFile)) {
                throw IOException("JARVIS could not safely replace the previously installed model.")
            }

            if (!temporaryFile.renameTo(modelFile)) {
                if (hadPreviousModel) backupFile.renameTo(modelFile)
                throw IOException("JARVIS could not finish installing the model.")
            }
            backupFile.delete()
            modelFile
        } finally {
            inferenceMutex.unlock()
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    /**
     * Generates text entirely on-device. Engine initialization may be slow on the
     * first request, so all loading and generation are moved off the main thread.
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().take(MAX_PROMPT_CHARACTERS)
        require(cleanPrompt.isNotEmpty()) { "The prompt is empty." }

        inferenceMutex.lock()
        try {
            check(isModelInstalled()) {
                "No local Gemma model is installed. Import the merged/exported .litertlm model first."
            }
            ensureEngineUnsafe()

            val output = StringBuilder()
            requireNotNull(conversation)
                .sendMessageAsync(cleanPrompt)
                .collect { messageChunk -> output.append(messageChunk.toString()) }

            output.toString().trim().ifBlank {
                throw IOException("The local model returned an empty response.")
            }
        } finally {
            inferenceMutex.unlock()
        }
    }

    private fun ensureEngineUnsafe() {
        if (engine != null && conversation != null) return
        check(isModelInstalled()) { "The local .litertlm model file is missing." }

        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IOException("JARVIS could not create the inference cache folder.")
        }

        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath
            )
        )
        try {
            candidate.initialize()
            val candidateConversation = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                    maxOutputToken = 512
                )
            )
            engine = candidate
            conversation = candidateConversation
        } catch (error: Exception) {
            runCatching { candidate.close() }
            throw error
        }
    }

    private fun closeEngineUnsafe() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
