package com.mindfortress.app.ai

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mindfortress.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

enum class AcousticEnvironment {
    QUIET_STUDY_SANCTUARY,
    MODERATE_AMBIENT,
    NOISY_CHAOS
}

data class SituationalContext(
    val ambientDb: Float,
    val environment: AcousticEnvironment,
    val lightLux: Float,
    val isDeviceStationary: Boolean,
    val isHeadsetConnected: Boolean,
    val isLateNightSleepWindow: Boolean,
    val recommendedAction: String? = null
)

class AmbientAcousticSentinel(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Audio sampling parameters
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = max(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        sampleRate / 4 // 250ms chunks
    )

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isMutedForPlayback = false
    private var recordJob: Job? = null
    private val prefs = PreferencesManager.getInstance(context)
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "AmbientSentinel coroutine caught exception safely: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Live Telemetry Flows
    private val _ambientDbFlow = MutableStateFlow(32.0f)
    val ambientDbFlow: StateFlow<Float> = _ambientDbFlow.asStateFlow()

    private val _environmentFlow = MutableStateFlow(AcousticEnvironment.QUIET_STUDY_SANCTUARY)
    val environmentFlow: StateFlow<AcousticEnvironment> = _environmentFlow.asStateFlow()

    private val _situationalContextFlow = MutableStateFlow(
        SituationalContext(
            ambientDb = 32.0f,
            environment = AcousticEnvironment.QUIET_STUDY_SANCTUARY,
            lightLux = 150f,
            isDeviceStationary = true,
            isHeadsetConnected = false,
            isLateNightSleepWindow = false
        )
    )
    val situationalContextFlow: StateFlow<SituationalContext> = _situationalContextFlow.asStateFlow()

    // Sensor state
    private var currentLux: Float = 150f
    private var lastAccelMagnitude: Float = 9.8f
    private var accelVariance: Float = 0.0f
    private var isStationary: Boolean = true

    // Listener for proactive triggers
    var onSituationalIntervention: ((String) -> Unit)? = null

    // Cooldown trackers
    private var lastNoiseAlertTime: Long = 0L
    private var lastCircadianAlertTime: Long = 0L

    fun startSentinel() {
        if (isRecording) return

        // 1. Register Hardware Sensors (Light & Accelerometer)
        try {
            if (prefs.isLightSensingActive) {
                lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            if (prefs.isMotionTrackingActive) {
                accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Sensor registration notice: ${e.message}")
        }

        // 2. Start Continuous Ambient Audio Recording Loop ONLY if explicit permission granted AND enabled
        if (!prefs.isAcousticRadarActive) {
            Log.i(TAG, "Acoustic radar disabled in preferences; standing by.")
            return
        }

        try {
            val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasAudioPermission) {
                Log.i(TAG, "Audio recording permission not yet granted; ambient radar standing by.")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true

                recordJob = scope.launch {
                    try {
                        val buffer = ShortArray(bufferSize)
                        while (isActive && isRecording) {
                            if (isMutedForPlayback) {
                                // Suppress listening while Jarvis speaks (prevent audio feedback loops)
                                kotlinx.coroutines.delay(100)
                                continue
                            }

                            val readCount = try {
                                audioRecord?.read(buffer, 0, buffer.size) ?: 0
                            } catch (e: Throwable) {
                                0
                            }

                            if (readCount > 0) {
                                var sumSquares = 0.0
                                for (i in 0 until readCount) {
                                    sumSquares += buffer[i] * buffer[i]
                                }
                                val rms = sqrt(sumSquares / readCount)
                                // Convert RMS to approximate decibels (calibrated for standard smartphone mic)
                                val db = if (rms > 1.0) {
                                    (20.0 * log10(rms)).toFloat()
                                } else 20.0f

                                val clampedDb = max(20.0f, minOf(110.0f, db))
                                updateAudioLevel(clampedDb)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Sentinel audio recording loop terminated safely: ${e.message}")
                    } finally {
                        try {
                            audioRecord?.stop()
                            audioRecord?.release()
                        } catch (ignored: Throwable) {}
                        audioRecord = null
                        isRecording = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio recording permission missing: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting Ambient Sentinel: ${e.message}")
        }
    }

    fun updateAudioLevel(clampedDb: Float) {
        _ambientDbFlow.value = clampedDb
        val env = when {
            clampedDb < 45.0f -> AcousticEnvironment.QUIET_STUDY_SANCTUARY
            clampedDb < 68.0f -> AcousticEnvironment.MODERATE_AMBIENT
            else -> AcousticEnvironment.NOISY_CHAOS
        }
        _environmentFlow.value = env
        try {
            evaluateSituationalContext(clampedDb, env)
        } catch (e: Throwable) {
            Log.w(TAG, "evaluateSituationalContext notice: ${e.message}")
        }
    }

    private fun evaluateSituationalContext(db: Float, env: AcousticEnvironment) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val isLateNight = hour >= 22 && minute >= 30 || hour < 6

        val isHeadset = try {
            val isWired = audioManager?.isWiredHeadsetOn == true
            val hasBtPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            val isBt = if (hasBtPerm) {
                try { audioManager?.isBluetoothA2dpOn == true } catch (e: Throwable) { false }
            } else false
            isWired || isBt
        } catch (e: Throwable) {
            false
        }

        var action: String? = null

        // 1. High Noise alert during study
        val currentTime = System.currentTimeMillis()
        if (env == AcousticEnvironment.NOISY_CHAOS && (currentTime - lastNoiseAlertTime > 15 * 60 * 1000L)) {
            lastNoiseAlertTime = currentTime
            action = "HIGH_NOISE_DETECTED"
            onSituationalIntervention?.invoke("Attention Vivek: High ambient noise detected (${db.toInt()} dB). Put on noise-cancelling headphones or relocate to your quiet sanctuary.")
        }

        // 2. Late Night Circadian Fatigue trigger (dark room + past 22:30)
        if (isLateNight && currentLux < 10.0f && (currentTime - lastCircadianAlertTime > 30 * 60 * 1000L)) {
            lastCircadianAlertTime = currentTime
            action = "CIRCADIAN_BEDTIME_TRIGGER"
            onSituationalIntervention?.invoke("Commander Vivek: It is past your 22:30 bedtime and room light is low. Power down screens to protect neuroplasticity and growth hormone release.")
        }

        _situationalContextFlow.value = SituationalContext(
            ambientDb = db,
            environment = env,
            lightLux = currentLux,
            isDeviceStationary = isStationary,
            isHeadsetConnected = isHeadset,
            isLateNightSleepWindow = isLateNight,
            recommendedAction = action
        )
    }

    // Feedback Suppression: Call before JARVIS speaks so it doesn't hear itself
    fun muteMicrophoneForTts(durationMs: Long) {
        isMutedForPlayback = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            isMutedForPlayback = false
        }, durationMs + 300L) // +300ms buffer for room echo clearance
    }

    fun stopSentinel() {
        isRecording = false
        try {
            recordJob?.cancel()
            recordJob = null
        } catch (ignored: Throwable) {}
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Throwable) {}
        audioRecord = null
        try {
            sensorManager?.unregisterListener(this)
        } catch (ignored: Throwable) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                currentLux = event.values[0]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = kotlin.math.abs(mag - lastAccelMagnitude)
                lastAccelMagnitude = mag
                accelVariance = accelVariance * 0.8f + delta * 0.2f
                isStationary = accelVariance < 0.25f // Desk rest threshold
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "AmbientSentinel"
    }
}
