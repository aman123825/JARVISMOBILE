package com.mindfortress.app.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fully local audio pipeline. Put the model files listed in README.md under app/src/main/assets/models/.
 * This class never opens a network connection and does not use Google Play Services.
 * All access to the native recognizers is serialized on the capture coroutine.
 */
class NativeSovereignVoiceEngine(context: Context) {
    enum class State {
        IDLE,
        SPEECH_DETECTED,
        STREAMING,
        UTTERANCE_COMPLETE
    }

    enum class ListeningMode {
        WAKE_WORD_COMMANDS,
        LIVE_CAPTIONS
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 512 // 32 ms at 16 kHz; Silero VAD window size
        private const val BYTES_PER_SAMPLE = 2
        private const val READ_BUFFER_SAMPLES = FRAME_SAMPLES
        private const val PRE_ROLL_FRAMES = 30 // 960 ms of local audio to preserve the command after wake-up
        private const val WAKE_WORD_NORMALIZED = "HEYJARVIS"
        private const val ASR_ENCODER = "models/asr/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
        private const val ASR_DECODER = "models/asr/decoder-epoch-99-avg-1-chunk-16-left-128.onnx"
        private const val ASR_JOINER = "models/asr/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
        private const val ASR_TOKENS = "models/asr/tokens.txt"
        private const val KWS_ENCODER = "models/kws/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val KWS_DECODER = "models/kws/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val KWS_JOINER = "models/kws/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val KWS_TOKENS = "models/kws/tokens.txt"
        private const val KWS_BPE_MODEL = "models/kws/bpe.model"
        private const val KWS_KEYWORDS = "models/kws/keywords.txt"
        private const val SILERO_VAD = "models/silero_vad.onnx"
    }

    private val appContext = context.applicationContext
    private val phraseCorrections = LocalPhraseCorrections(appContext)
    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _listeningMode = MutableStateFlow(ListeningMode.WAKE_WORD_COMMANDS)
    val listeningMode: StateFlow<ListeningMode> = _listeningMode.asStateFlow()

    /** Emits partial text while speaking; the last value remains visible until the next wake word. */
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    /** The latest finalized spoken command, with the wake phrase removed. */
    private val _latestCommand = MutableStateFlow("")
    val latestCommand: StateFlow<String> = _latestCommand.asStateFlow()

    /** Latest completed hearing caption. Captions are never sent to the command event stream. */
    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript.asStateFlow()

    /** Event stream so repeated identical phrases can still appear as separate captions. */
    private val _captionEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val captionEvents: SharedFlow<String> = _captionEvents.asSharedFlow()

    /** Event stream for command routers. Keep actions behind a permission/confirmation policy. */
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _vadSpeechActive = MutableStateFlow(false)
    val vadSpeechActive: StateFlow<Boolean> = _vadSpeechActive.asStateFlow()

    private val _audioDb = MutableStateFlow(25.0f)
    val audioDb: StateFlow<Float> = _audioDb.asStateFlow()

    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var closed = false
    private var modelsLoaded = false
    private var awake = false

    private var vad: Vad? = null
    private var keywordSpotter: KeywordSpotter? = null
    private var keywordStream: OnlineStream? = null
    private var recognizer: OnlineRecognizer? = null
    private var asrStream: OnlineStream? = null

    private val preRoll = ArrayDeque<FloatArray>()
    private var lastUtteranceEndMs = 0L

    /** Preload neural network weights without opening the microphone. */
    suspend fun preloadModels() = withContext(Dispatchers.Default) {
        lifecycleMutex.withLock {
            if (closed || modelsLoaded) return@withLock
            _isModelLoading.value = true
            try {
                loadModels()
                _isReady.value = true
            } catch (t: Throwable) {
                _error.value = "Model preload failed: ${t.message}"
            } finally {
                _isModelLoading.value = false
            }
        }
    }

    /** Start the owner-verified wake-word command mode. */
    suspend fun start() = startWithMode(ListeningMode.WAKE_WORD_COMMANDS)

    /** Start continuous local captions until pause/close; captions are not executable commands. */
    suspend fun startCaptions() = startWithMode(ListeningMode.LIVE_CAPTIONS)

    /** Load model weights and start microphone capture. Must be called while the UI is visible. */
    suspend fun resume() = startWithMode(null)

    private suspend fun startWithMode(mode: ListeningMode?) = lifecycleMutex.withLock {
        if (mode != null) _listeningMode.value = mode
        check(!closed) { "Voice engine has been closed" }
        if (captureJob?.isActive == true) return@withLock
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission has not been granted")
        }

        _error.value = null
        if (!modelsLoaded) {
            _isModelLoading.value = true
            try {
                withContext(Dispatchers.Default) { loadModels() }
                _isReady.value = true
            } catch (t: Throwable) {
                _isModelLoading.value = false
                _error.value = "Model load error: ${t.message}"
                throw t
            }
            _isModelLoading.value = false
        }

        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBytes > 0) { "AudioRecord does not support 16 kHz mono PCM on this device" }

        if (!MicrophoneCaptureArbiter.tryAcquire(this)) {
            throw IllegalStateException(
                "The JARVIS microphone is already in use by another audio feature. Pause hands-free listening or acoustic capture, then retry."
            )
        }

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBytes * 2, READ_BUFFER_SAMPLES * BYTES_PER_SAMPLE * 4))
                .build()
        } catch (t: Throwable) {
            MicrophoneCaptureArbiter.release(this)
            throw t
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            MicrophoneCaptureArbiter.release(this)
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            recorder.release()
            MicrophoneCaptureArbiter.release(this)
            throw t
        }

        audioRecord = recorder
        captureJob = scope.launch { captureLoop(recorder) }
    }

    /** Immediately trigger streaming ASR (push-to-talk) without requiring the 'Hey Jarvis' keyword. */
    fun forceWake() {
        scope.launch {
            try {
                // Do not call start() while holding lifecycleMutex: start() also takes that mutex.
                if (audioRecord == null) start()
                lifecycleMutex.withLock {
                    if (closed || audioRecord == null) return@withLock
                    _listeningMode.value = ListeningMode.WAKE_WORD_COMMANDS
                    activateAsrFromPreRoll()
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    /** Stops and releases only the microphone. Native model weights stay loaded for quick resume. */
    suspend fun pause() = lifecycleMutex.withLock {
        stopCaptureLocked()
        resetUtteranceState()
        _state.value = State.IDLE
    }


    /**
     * Disposal-safe shutdown for Compose/service lifecycles. The closure runs on the
     * engine-owned scope, so a caller's UI/service scope can be cancelled independently.
     */
    fun closeAsync(onClosed: (() -> Unit)? = null) {
        scope.launch {
            try {
                close()
            } finally {
                onClosed?.invoke()
            }
        }
    }

    /** Stops capture and releases all native model handles. Safe to call more than once. */
    suspend fun close() = lifecycleMutex.withLock {
        if (closed) return@withLock
        stopCaptureLocked()
        resetUtteranceState()
        withContext(Dispatchers.Default) {
            asrStream?.release()
            asrStream = null
            keywordStream?.release()
            keywordStream = null
            recognizer?.release()
            recognizer = null
            keywordSpotter?.release()
            keywordSpotter = null
            vad?.release()
            vad = null
        }
        modelsLoaded = false
        closed = true
        _state.value = State.IDLE
        scope.cancel()
    }

    private suspend fun stopCaptureLocked() {
        val recorder = audioRecord
        audioRecord = null
        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (_: IllegalStateException) {
        }
        captureJob?.cancelAndJoin()
        captureJob = null
        try {
            recorder?.release()
        } catch (_: Throwable) {
        }
        MicrophoneCaptureArbiter.release(this)
    }

    private fun loadModels() {
        val requiredAssets = listOf(
            ASR_ENCODER, ASR_DECODER, ASR_JOINER, ASR_TOKENS,
            KWS_ENCODER, KWS_DECODER, KWS_JOINER, KWS_TOKENS, KWS_BPE_MODEL, KWS_KEYWORDS,
            SILERO_VAD
        )
        requiredAssets.forEach { path ->
            appContext.assets.open(path).use { }
        }

        try {
        val asrModelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = ASR_ENCODER,
                decoder = ASR_DECODER,
                joiner = ASR_JOINER
            ),
            tokens = ASR_TOKENS,
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "zipformer"
        )
        recognizer = OnlineRecognizer(
            appContext.assets,
            OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
                modelConfig = asrModelConfig,
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 1.0f, 0.0f),
                    rule2 = EndpointRule(true, 0.45f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 12.0f)
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )
        )

        val kwsModelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = KWS_ENCODER,
                decoder = KWS_DECODER,
                joiner = KWS_JOINER
            ),
            tokens = KWS_TOKENS,
            numThreads = 1,
            debug = false,
            provider = "cpu",
            modelType = "zipformer",
            modelingUnit = "bpe",
            bpeVocab = KWS_BPE_MODEL
        )
        keywordSpotter = KeywordSpotter(
            appContext.assets,
            KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
                modelConfig = kwsModelConfig,
                maxActivePaths = 4,
                keywordsFile = KWS_KEYWORDS,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f,
                numTrailingBlanks = 2
            )
        )
        keywordStream = keywordSpotter!!.createStream()
        vad = Vad(
            appContext.assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = SILERO_VAD,
                    threshold = 0.5f,
                    minSilenceDuration = 0.35f,
                    minSpeechDuration = 0.15f,
                    windowSize = FRAME_SAMPLES,
                    maxSpeechDuration = 12.0f
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )
        )
        modelsLoaded = true
        } catch (t: Throwable) {
            asrStream?.release()
            asrStream = null
            keywordStream?.release()
            keywordStream = null
            recognizer?.release()
            recognizer = null
            keywordSpotter?.release()
            keywordSpotter = null
            vad?.release()
            vad = null
            modelsLoaded = false
            throw t
        }
    }

    private suspend fun captureLoop(recorder: AudioRecord) {
        val readBuffer = ShortArray(READ_BUFFER_SAMPLES)
        val frame = ShortArray(FRAME_SAMPLES)
        var filled = 0
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val count = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw IllegalStateException("Audio input device was disconnected")
                }
                if (count < 0) {
                    throw IllegalStateException("AudioRecord read failed: $count")
                }
                if (count == 0) {
                    delay(5)
                    continue
                }
                var src = 0
                while (src < count) {
                    val amount = min(FRAME_SAMPLES - filled, count - src)
                    System.arraycopy(readBuffer, src, frame, filled, amount)
                    filled += amount
                    src += amount
                    if (filled == FRAME_SAMPLES) {
                        processFrame(frame)
                        filled = 0
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            _error.value = t.message ?: t.javaClass.simpleName
            _state.value = State.IDLE
        } finally {
            // If capture failed unexpectedly (rather than being paused), release the recorder.
            if (audioRecord === recorder) {
                audioRecord = null
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                }
                runCatching { recorder.release() }
            }
            MicrophoneCaptureArbiter.release(this)
        }
    }

    /** Runs on the capture coroutine: VAD, tiny wake-word network, and ASR never contend for the mic. */
    private fun processFrame(pcmFrame: ShortArray) {
        val samples = FloatArray(FRAME_SAMPLES) { i -> pcmFrame[i].toFloat() / 32768.0f }
        val vadSpeaking = vad?.let { detector ->
            detector.acceptWaveform(samples)
            val active = detector.isSpeechDetected()
            // We use VAD as a low-cost speech gate/diagnostic, not as the ASR segment store.
            // Drain completed segments so a long-running monitor cannot accumulate old audio.
            while (!detector.empty()) detector.pop()
            active
        } ?: false
        _vadSpeechActive.value = vadSpeaking

        var sumSquares = 0.0
        for (i in 0 until FRAME_SAMPLES) {
            val s = samples[i]
            sumSquares += (s * s).toDouble()
        }
        val rms = sqrt(sumSquares / FRAME_SAMPLES)
        val db = if (rms > 0.0001) {
            (20.0 * log10(rms) + 90.0).toFloat().coerceIn(20f, 105f)
        } else 20.0f
        _audioDb.value = db

        if (_listeningMode.value == ListeningMode.LIVE_CAPTIONS) {
            // Hearing mode intentionally streams without a wake phrase. It is user-started,
            // visible in the foreground UI, and never emits executable command events.
            if (!awake) {
                asrStream?.release()
                asrStream = recognizer?.createStream() ?: return
                awake = true
                _partialTranscript.value = ""
                _state.value = State.IDLE
            }
            processAsrFrame(samples, allowEndpoint = true)
            return
        }

        if (!awake) {
            preRoll.addLast(samples.copyOf())
            while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            val spotter = keywordSpotter ?: return
            val stream = keywordStream ?: return
            stream.acceptWaveform(samples, SAMPLE_RATE)
            var wakeDetected = false
            while (spotter.isReady(stream)) {
                spotter.decode(stream)
                val detected = spotter.getResult(stream).keyword
                if (normalizeWakeWord(detected) == WAKE_WORD_NORMALIZED) {
                    wakeDetected = true
                    break
                }
            }
            if (wakeDetected && vadSpeaking) {
                activateAsrFromPreRoll()
            } else if (wakeDetected) {
                // Ignore a wake-word false positive when the VAD does not confirm speech.
                spotter.reset(stream)
            }
        } else {
            processAsrFrame(samples, allowEndpoint = true)
        }
    }

    private fun activateAsrFromPreRoll() {
        val localRecognizer = recognizer ?: return
        keywordStream?.let { keywordSpotter?.reset(it) }
        asrStream?.release()
        asrStream = localRecognizer.createStream()
        awake = true
        _partialTranscript.value = ""
        _state.value = State.SPEECH_DETECTED
        if (asrStream == null) return
        // Replay the short ring buffer so the first words spoken immediately after the wake phrase survive.
        preRoll.forEach { frame -> processAsrFrame(frame, allowEndpoint = false) }
        preRoll.clear()
    }

    private fun processAsrFrame(samples: FloatArray, allowEndpoint: Boolean) {
        val localRecognizer = recognizer ?: return
        val stream = asrStream ?: return
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (localRecognizer.isReady(stream)) {
            localRecognizer.decode(stream)
            val partial = localRecognizer.getResult(stream).text.trim()
            if (partial.isNotEmpty()) {
                _partialTranscript.value = partial
                _state.value = State.STREAMING
            }
        }
        if (allowEndpoint && localRecognizer.isEndpoint(stream)) finishUtterance()
    }

    private fun finishUtterance() {
        val stream = asrStream ?: return
        val decodedText = recognizer?.getResult(stream)?.text?.trim().orEmpty()
        stream.release()
        asrStream = null
        _state.value = State.UTTERANCE_COMPLETE

        if (_listeningMode.value == ListeningMode.LIVE_CAPTIONS) {
            val caption = phraseCorrections.apply(decodedText)
            _partialTranscript.value = ""
            if (caption.isNotBlank()) {
                _finalTranscript.value = caption
                _captionEvents.tryEmit(caption)
            }
            awake = false
            preRoll.clear()
            return
        }

        val fullText = phraseCorrections.apply(decodedText)
        _partialTranscript.value = fullText
        val command = stripWakeWord(fullText)
        if (command.isNotBlank()) {
            _latestCommand.value = command
            _commands.tryEmit(command)
        }
        awake = false
        preRoll.clear()
        keywordStream?.let { keywordSpotter?.reset(it) }
        lastUtteranceEndMs = SystemClock.elapsedRealtime()
        scope.launch {
            delay(280)
            if (!awake && SystemClock.elapsedRealtime() - lastUtteranceEndMs >= 250L && _state.value == State.UTTERANCE_COMPLETE) {
                _state.value = State.IDLE
            }
        }
    }

    fun savePhraseCorrection(original: String, corrected: String): Boolean =
        phraseCorrections.save(original, corrected)

    fun removePhraseCorrection(original: String) =
        phraseCorrections.remove(original)

    /** Returns the local, user-authored correction pairs for the hearing companion settings UI. */
    fun phraseCorrections(): Map<String, String> = phraseCorrections.all()

    private fun resetUtteranceState() {
        awake = false
        preRoll.clear()
        asrStream?.release()
        asrStream = null
        keywordStream?.let { keywordSpotter?.reset(it) }
        vad?.reset()
        _vadSpeechActive.value = false
        _partialTranscript.value = ""
    }

    private fun normalizeWakeWord(text: String): String =
        text.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun stripWakeWord(text: String): String {
        val normalized = text.trim()
        val match = Regex("(?i)\\bhey\\s+jarvis\\b").find(normalized)
        return if (match == null) normalized else normalized.substring(match.range.last + 1)
            .trimStart(' ', ',', '.', ':', ';', '!', '?', '-')
            .trim()
    }
}
