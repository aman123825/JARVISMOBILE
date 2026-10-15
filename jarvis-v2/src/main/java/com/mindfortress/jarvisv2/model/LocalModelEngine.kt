package com.mindfortress.jarvisv2.model

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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** No model load, network request, microphone access or background service in the constructor. */
class LocalModelEngine(context: Context) {
    companion object {
        const val MODEL_NAME = "jarvis-local-model.litertlm"
        private const val RESERVE_BYTES = 384L * 1024L * 1024L
        private const val MIN_MODEL_BYTES = ModelFilePolicy.MINIMUM_MODEL_BYTES
        private const val RESPONSE_TIMEOUT_MS = 60_000L
        private const val MAX_PROMPT_CHARACTERS = 8_000
        private const val SYSTEM_PROMPT = """
You are JARVIS, a helpful personal AI assistant running locally on this Android device.
Answer greetings immediately, be natural and direct, and do not over-explain simple requests.
For technical and STEM questions, explain clearly step by step when requested.
Never claim to have read phone data or completed an action unless the app explicitly provided that data or confirmed the action.
Do not invent current facts. You may be offline.
"""
    }

    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "models")
    private val modelFile = File(modelDirectory, MODEL_NAME)
    private val cacheDirectory = File(appContext.cacheDir, "litertlm-v2")
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun isModelInstalled(): Boolean = modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES

    suspend fun installModel(uri: Uri): Long = withContext(Dispatchers.IO) {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Could not create the private model folder.")
        }

        val metadata = appContext.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                name to size
            } else null
        }

        val displayName = metadata?.first
        val expectedSize = metadata?.second ?: -1L
        if (!ModelFilePolicy.isSupportedFileName(displayName)) {
            throw IOException("Select an exported .litertlm file. Adapter weights and checkpoint ZIPs cannot run directly.")
        }
        if (expectedSize > 0 && expectedSize < MIN_MODEL_BYTES) {
            throw IOException("The selected file is too small to be a complete model.")
        }
        if (expectedSize > 0 &&
            StatFs(modelDirectory.absolutePath).availableBytes < expectedSize + RESERVE_BYTES
        ) {
            throw IOException("Not enough free phone storage. Free more space and retry.")
        }

        val partFile = File(modelDirectory, "$MODEL_NAME.part")
        val backupFile = File(modelDirectory, "$MODEL_NAME.previous")
        partFile.delete()
        var copied = 0L

        try {
            val source = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Android could not open the selected model.")
            source.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                    }
                    output.fd.sync()
                }
            }

            if (copied < MIN_MODEL_BYTES) {
                throw IOException("The selected file is incomplete or is not a full LiteRT-LM model.")
            }
            if (expectedSize > 0 && copied != expectedSize) {
                throw IOException("The model copy was incomplete. Expected $expectedSize bytes but copied $copied.")
            }

            mutex.withLock {
                closeEngineUnsafe()
                backupFile.delete()
                val hadOld = modelFile.exists()
                if (hadOld && !modelFile.renameTo(backupFile)) {
                    throw IOException("Could not safely back up the installed model.")
                }
                if (!partFile.renameTo(modelFile)) {
                    if (hadOld) backupFile.renameTo(modelFile)
                    throw IOException("Could not finish installing the model.")
                }
                backupFile.delete()
            }
            copied
        } catch (error: Throwable) {
            partFile.delete()
            throw error
        } finally {
            partFile.delete()
        }
    }

    suspend fun respond(rawPrompt: String): String = withContext(Dispatchers.IO) {
        val prompt = rawPrompt.trim().take(MAX_PROMPT_CHARACTERS)
        require(prompt.isNotEmpty()) { "Enter a message first." }

        mutex.withLock {
            if (!isModelInstalled()) {
                throw IOException("No local AI model is installed. Tap IMPORT MODEL and choose an exported .litertlm file.")
            }

            withTimeout(RESPONSE_TIMEOUT_MS) {
                ensureEngineUnsafe()
                val output = StringBuilder()
                requireNotNull(conversation)
                    .sendMessageAsync(prompt)
                    .collect { part -> output.append(part.toString()) }
                output.toString().trim().ifBlank {
                    throw IOException("The local model returned no text. Try a shorter prompt.")
                }
            }
        }
    }

    suspend fun resetConversation() = withContext(Dispatchers.IO) {
        mutex.withLock { closeEngineUnsafe() }
    }

    suspend fun close() = resetConversation()

    private fun ensureEngineUnsafe() {
        if (engine != null && conversation != null) return
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IOException("Could not create the local inference cache.")
        }

        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(threadCount = 4),
                cacheDir = cacheDirectory.absolutePath
            )
        )
        try {
            candidate.initialize()
            val candidateConversation = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM_PROMPT),
                    maxOutputToken = 384,
                    prefillPrefaceOnInit = true
                )
            )
            engine = candidate
            conversation = candidateConversation
        } catch (error: Throwable) {
            if (candidate.isInitialized()) runCatching { candidate.close() }
            throw error
        }
    }

    private fun closeEngineUnsafe() {
        runCatching { conversation?.close() }
        conversation = null
        val previous = engine
        engine = null
        if (previous?.isInitialized() == true) runCatching { previous.close() }
    }
}
