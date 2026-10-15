package com.mindfortress.app.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.mindfortress.app.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Optional on-device language model runtime using LiteRT-LM.
 *
 * The user supplies a compatible .litertlm file. Model weights remain in app-private
 * storage and inference is local; this class itself does not make network requests.
 * CPU is the compatibility-first backend; hardware acceleration can be added only after
 * benchmarking and validating the specific device/model/backend combination.
 */
class OnDeviceLlmRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = PreferencesManager.getInstance(appContext)
    private val lifecycleMutex = Mutex()

    @Volatile private var loadedPath: String? = null
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    val configuredModelPath: String
        get() = preferences.localLlmModelPath

    val configuredModelFile: File?
        get() = configuredModelPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile && it.length() > 0L }

    val isModelConfigured: Boolean
        get() = configuredModelFile != null

    val isModelLoaded: Boolean
        get() = loadedPath != null && loadedPath == configuredModelPath

    /**
     * Copy a model chosen through Android's document picker to app-private storage.
     * A temporary file is used so an interrupted copy cannot replace a working model.
     */
    suspend fun importModel(uri: Uri): File = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "assistant-model.litertlm"
        if (!displayName.endsWith(".litertlm", ignoreCase = true)) {
            throw IllegalArgumentException("Choose a LiteRT-LM model file ending in .litertlm.")
        }

        val modelDirectory = File(appContext.filesDir, "models")
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Could not create the private model directory.")
        }

        val target = File(modelDirectory, "assistant-model.litertlm")
        val staging = File(modelDirectory, "assistant-model.litertlm.part")
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Android could not open the selected file.")
            input.use { source ->
                staging.outputStream().buffered().use { sink ->
                    source.copyTo(sink, DEFAULT_BUFFER_SIZE)
                }
            }
            if (!staging.isFile || staging.length() <= 0L) {
                throw IOException("The selected model file is empty.")
            }
            lifecycleMutex.withLock {
                // Never replace the underlying file while the native runtime may still map it.
                closeLoadedModelLocked()
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not replace the previous model file.")
                }
                if (!staging.renameTo(target)) {
                    staging.copyTo(target, overwrite = true)
                    if (!staging.delete()) staging.deleteOnExit()
                }
                preferences.localLlmModelPath = target.absolutePath
            }
            target
        } catch (t: Throwable) {
            staging.delete()
            throw t
        }
    }

    /**
     * Runs a prompt on-device and returns null if no model has been selected.
     * The conversation is retained between calls so follow-up questions have context.
     */
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.Default) {
        lifecycleMutex.withLock {
            val modelFile = configuredModelFile ?: return@withLock null
            val modelPath = modelFile.absolutePath
            if (loadedPath != modelPath || conversation == null) {
                closeLoadedModelLocked()
                try {
                    val newEngine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.CPU(),
                            cacheDir = appContext.cacheDir.absolutePath
                        )
                    )
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                            samplerConfig = SamplerConfig(
                                topK = 32,
                                topP = 0.9,
                                temperature = 0.3
                            )
                        )
                    )
                    engine = newEngine
                    conversation = newConversation
                    loadedPath = modelPath
                } catch (t: Throwable) {
                    closeLoadedModelLocked()
                    Log.e(TAG, "Could not initialize the selected on-device language model", t)
                    throw t
                }
            }

            val output = StringBuilder()
            conversation!!.sendMessageAsync(prompt).collect { message ->
                output.append(message.toString())
            }
            output.toString().trim().takeIf { it.isNotBlank() }
        }
    }

    suspend fun removeModel() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            closeLoadedModelLocked()
            val file = configuredModelPath.takeIf { it.isNotBlank() }?.let(::File)
            preferences.localLlmModelPath = ""
            file?.delete()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock { closeLoadedModelLocked() }
    }

    private fun closeLoadedModelLocked() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) cursor.getString(column) else null
                } else null
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OnDeviceLlmRuntime"
        private const val SYSTEM_INSTRUCTION = """
You are JARVIS, an accurate, rigorous on-device research and engineering assistant.

Quality rules:
- Explain from first principles when useful. State assumptions, definitions, units, derivations, edge cases, and limitations.
- Distinguish verified facts from inference and uncertainty. Never invent citations, experiments, measurements, APIs, or capabilities.
- For mathematics and engineering, show intermediate reasoning, check dimensions, and test results where possible.
- For research requests, outline search terms, compare evidence, and identify what requires current external verification. This model is offline and must not claim it searched the internet.
- Prefer concise direct answers first, then enough detail for the user to act.
- Device actions are handled by a separate deterministic router. Do not claim an action happened unless the app confirms it.
- Do not claim to outperform every researcher. Aim for precision, humility, and reproducibility.
"""
    }
}
