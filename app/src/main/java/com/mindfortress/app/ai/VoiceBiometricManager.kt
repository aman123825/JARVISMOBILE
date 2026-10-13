package com.mindfortress.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceBiometricManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_biometrics_prefs", Context.MODE_PRIVATE)

    var isEnrolled: Boolean
        get() = prefs.getBoolean(KEY_IS_ENROLLED, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_ENROLLED, value).apply()

    /**
     * Enrolls voice profile using multiple audio buffers, averaging the resulting templates.
     * Rejects buffers that do not meet the minimum audio energy threshold.
     */
    fun enrollSpeaker(audioBuffers: List<ShortArray>): Boolean {
        if (audioBuffers.isEmpty()) return false

        // Filter buffers by minimum energy requirement to reject silence/noise
        val validBuffers = audioBuffers.filter { calculateRms(it) >= MIN_AUDIO_ENERGY_RMS }
        if (validBuffers.isEmpty()) {
            Log.w(TAG, "Enrollment failed: All audio samples rejected due to low energy/silence.")
            return false
        }

        // Extract and accumulate normalized feature vectors across multiple enrollment samples
        val accumulatedTemplate = FloatArray(FEATURE_DIM)
        var sampleCount = 0

        for (buffer in validBuffers) {
            val vec = extractAcousticFeatures(buffer)
            val norm = l2Norm(vec)
            if (norm > 0f) {
                for (i in 0 until FEATURE_DIM) {
                    accumulatedTemplate[i] += (vec[i] / norm)
                }
                sampleCount++
            }
        }

        if (sampleCount == 0) return false

        // Average templates
        for (i in 0 until FEATURE_DIM) {
            accumulatedTemplate[i] /= sampleCount
        }

        // Normalize the averaged template
        val finalNorm = l2Norm(accumulatedTemplate)
        if (finalNorm > 0f) {
            for (i in 0 until FEATURE_DIM) accumulatedTemplate[i] /= finalNorm
        }

        // Persist serialized template
        val serialized = accumulatedTemplate.joinToString(",") { it.toString() }
        prefs.edit().putString(KEY_VOICE_TEMPLATE, serialized).apply()
        isEnrolled = true
        Log.i(TAG, "Voice biometric profile enrolled successfully from $sampleCount sample(s).")
        return true
    }

    /**
     * Overload to enroll from a single continuous audio recording (e.g. 3-second buffer).
     * Automatically splits long recordings into multiple overlapping chunks for template averaging.
     */
    fun enrollSpeaker(audioSample: ShortArray): Boolean {
        if (calculateRms(audioSample) < MIN_AUDIO_ENERGY_RMS) {
            Log.w(TAG, "Enrollment audio sample rejected: Insufficient energy (silence/noise).")
            return false
        }

        // If recording is long enough (>= 32,000 samples / 2 seconds), segment into 3 samples
        val buffers = if (audioSample.size >= 32000) {
            val windowSize = 16000 // 1 second
            val midStart = (audioSample.size - windowSize) / 2
            val lastStart = audioSample.size - windowSize
            listOf(
                audioSample.copyOfRange(0, windowSize),
                audioSample.copyOfRange(midStart, midStart + windowSize),
                audioSample.copyOfRange(lastStart, audioSample.size)
            )
        } else {
            listOf(audioSample)
        }

        return enrollSpeaker(buffers)
    }

    /**
     * Compatibility alias for existing callers.
     */
    fun enrollVoiceProfile(audioBuffers: List<ShortArray>): Boolean {
        return enrollSpeaker(audioBuffers)
    }

    /**
     * Verifies whether the incoming audio matches the enrolled voice template.
     */
    fun verifySpeaker(audioSample: ShortArray): Pair<Boolean, Float> {
        val templateStr = prefs.getString(KEY_VOICE_TEMPLATE, null) ?: return Pair(true, 1.0f) // Fallback if not enrolled
        val templateVals = templateStr.split(",").mapNotNull { it.toFloatOrNull() }
        if (templateVals.size != FEATURE_DIM) return Pair(true, 1.0f)

        // Minimum audio energy check: reject silence or low ambient noise
        val rms = calculateRms(audioSample)
        if (rms < MIN_AUDIO_ENERGY_RMS) {
            Log.d(TAG, "Speaker verification rejected: Audio energy too low (RMS: $rms < $MIN_AUDIO_ENERGY_RMS)")
            return Pair(false, 0.0f)
        }

        val incomingVec = extractAcousticFeatures(audioSample)
        val norm = l2Norm(incomingVec)
        if (norm > 0f) {
            for (i in 0 until FEATURE_DIM) incomingVec[i] /= norm
        }

        var dotProduct = 0f
        for (i in 0 until FEATURE_DIM) {
            dotProduct += incomingVec[i] * templateVals[i]
        }

        val isMatch = dotProduct >= MATCH_THRESHOLD
        Log.d(TAG, "Speaker verification score: $dotProduct (Threshold: $MATCH_THRESHOLD -> Match: $isMatch)")
        return Pair(isMatch, dotProduct)
    }

    /**
     * Estimates vocal stress / strain biomarker index (0.0f = calm, 1.0f = high stress)
     * by computing pitch period jitter (micro-perturbations) and spectral crest factor.
     */
    fun estimateVocalStress(audioSample: ShortArray): Float {
        if (audioSample.isEmpty()) return 0.0f
        val rms = calculateRms(audioSample)
        if (rms < MIN_AUDIO_ENERGY_RMS) return 0.0f

        val frameSize = 480 // ~30ms at 16kHz
        val hopSize = 240   // 15ms hop
        if (audioSample.size < frameSize) return 0.0f

        val numFrames = (audioSample.size - frameSize) / hopSize + 1
        val pitchPeriods = mutableListOf<Float>()
        var totalCrestFactor = 0.0
        var voicedFramesCount = 0

        val minLag = 32   // ~500 Hz
        val maxLag = 320  // ~50 Hz

        for (f in 0 until numFrames) {
            val offset = f * hopSize
            val frameRms = calculateRms(audioSample, offset, offset + frameSize)
            if (frameRms < MIN_AUDIO_ENERGY_RMS * 0.8) continue // unvoiced or silence

            // 1. Normalized Autocorrelation for pitch period detection
            var maxCorr = 0.0
            var bestLag = 0

            // Energy at zero lag
            var e0 = 0.0
            for (i in 0 until (frameSize - maxLag)) {
                val s = audioSample[offset + i].toDouble()
                e0 += s * s
            }

            if (e0 > 1e-4) {
                for (lag in minLag..maxLag) {
                    var corr = 0.0
                    var eLag = 0.0
                    for (i in 0 until (frameSize - maxLag)) {
                        val s1 = audioSample[offset + i].toDouble()
                        val s2 = audioSample[offset + i + lag].toDouble()
                        corr += s1 * s2
                        eLag += s2 * s2
                    }
                    val normFactor = sqrt(e0 * eLag)
                    if (normFactor > 0) {
                        val normCorr = corr / normFactor
                        if (normCorr > maxCorr) {
                            maxCorr = normCorr
                            bestLag = lag
                        }
                    }
                }
            }

            if (maxCorr >= 0.35 && bestLag > 0) {
                pitchPeriods.add(bestLag.toFloat())
            }

            // 2. Spectral Crest Factor for this frame
            val fftSize = 512
            val real = FloatArray(fftSize)
            val imag = FloatArray(fftSize)
            for (i in 0 until frameSize.coerceAtMost(fftSize)) {
                val win = 0.5 * (1.0 - cos(2.0 * PI * i / (frameSize - 1)))
                real[i] = (audioSample[offset + i] * win).toFloat()
            }
            fft(real, imag)

            var frameMaxMag = 0.0
            var frameTotalMag = 0.0
            val halfBins = fftSize / 2
            for (k in 1 until halfBins) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]).toDouble()
                frameTotalMag += mag
                if (mag > frameMaxMag) frameMaxMag = mag
            }
            val frameMeanMag = frameTotalMag / (halfBins - 1).coerceAtLeast(1)
            if (frameMeanMag > 1e-6) {
                val crest = (frameMaxMag / frameMeanMag)
                totalCrestFactor += crest
                voicedFramesCount++
            }
        }

        // Calculate Pitch Jitter
        val jitterScore = if (pitchPeriods.size >= 3) {
            var diffSum = 0.0
            var periodSum = 0.0
            for (i in 1 until pitchPeriods.size) {
                diffSum += kotlin.math.abs(pitchPeriods[i] - pitchPeriods[i - 1])
                periodSum += pitchPeriods[i]
            }
            periodSum += pitchPeriods[0]
            val meanPeriod = periodSum / pitchPeriods.size
            val avgDiff = diffSum / (pitchPeriods.size - 1)
            val relativeJitter = if (meanPeriod > 0) (avgDiff / meanPeriod) else 0.0

            // Normal calm speech: ~0.01; Elevated stress / vocal strain: > 0.04 - 0.08
            ((relativeJitter - 0.01) / 0.05).coerceIn(0.0, 1.0).toFloat()
        } else {
            0.2f // baseline default when pitch estimation has insufficient voiced periods
        }

        // Calculate Average Spectral Crest
        val crestScore = if (voicedFramesCount > 0) {
            val avgCrest = totalCrestFactor / voicedFramesCount
            // Typical speech: ~3.5; High tension / acoustic peakiness: > 8.0
            ((avgCrest - 3.5) / 5.0).coerceIn(0.0, 1.0).toFloat()
        } else {
            0.2f
        }

        val strainIndex = (0.55f * jitterScore + 0.45f * crestScore).coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Vocal stress estimation: jitterScore=$jitterScore, crestScore=$crestScore -> strainIndex=$strainIndex")
        return strainIndex
    }

    /**
     * Extracts a 64-dimensional feature vector combining spectral (FFT sub-bands & shape)
     * and temporal acoustic properties.
     */
    fun extractAcousticFeatures(buffer: ShortArray): FloatArray {
        val features = FloatArray(FEATURE_DIM)
        if (buffer.isEmpty()) return features

        val frameSize = 512
        val hopSize = 256
        if (buffer.size < frameSize) {
            // Buffer too short for FFT framing, fallback to time-domain approximation
            return extractFallbackTimeFeatures(buffer)
        }

        val numFrames = (buffer.size - frameSize) / hopSize + 1
        var validFrames = 0

        val avgBandEnergies = FloatArray(NUM_SPECTRAL_BANDS)
        var totalCentroid = 0.0
        var totalSpread = 0.0
        var totalRolloff = 0.0
        var totalFlatness = 0.0
        var totalCrest = 0.0
        var totalLowEnergy = 0.0
        var totalSpeechEnergy = 0.0
        var totalHighEnergy = 0.0
        var totalFlux = 0.0

        val prevSpectrum = FloatArray(frameSize / 2)
        val real = FloatArray(frameSize)
        val imag = FloatArray(frameSize)

        for (f in 0 until numFrames) {
            val offset = f * hopSize

            // Check frame energy
            var frameSumSq = 0.0
            for (i in 0 until frameSize) {
                val s = buffer[offset + i].toDouble()
                frameSumSq += s * s
            }
            val frameRms = sqrt(frameSumSq / frameSize)
            if (frameRms < 100.0) continue // Skip silent frames

            // Apply Hann window
            for (i in 0 until frameSize) {
                val win = 0.5 * (1.0 - cos(2.0 * PI * i / (frameSize - 1)))
                real[i] = (buffer[offset + i] * win).toFloat()
                imag[i] = 0f
            }

            // Radix-2 Cooley-Tukey FFT
            fft(real, imag)

            // Magnitude spectrum for positive frequencies (256 bins)
            val numBins = frameSize / 2
            val mag = FloatArray(numBins)
            var totalFrameMag = 0.0
            for (k in 0 until numBins) {
                mag[k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
                totalFrameMag += mag[k]
            }

            if (totalFrameMag <= 1e-6) continue
            validFrames++

            // 1. 32 Spectral Sub-band log energies (Bins 2 to 226, ~62 Hz to 7062 Hz)
            val binsPerBand = 7
            for (b in 0 until NUM_SPECTRAL_BANDS) {
                val startBin = 2 + b * binsPerBand
                val endBin = (startBin + binsPerBand).coerceAtMost(numBins)
                var bandEnergy = 0.0
                for (k in startBin until endBin) {
                    bandEnergy += (mag[k] * mag[k]).toDouble()
                }
                avgBandEnergies[b] += ln(1.0 + bandEnergy).toFloat()
            }

            // 2. Spectral Centroid
            var centroidNum = 0.0
            for (k in 0 until numBins) {
                centroidNum += k * mag[k].toDouble()
            }
            val centroid = centroidNum / totalFrameMag
            totalCentroid += centroid

            // 3. Spectral Spread
            var spreadNum = 0.0
            for (k in 0 until numBins) {
                val diff = k - centroid
                spreadNum += diff * diff * mag[k].toDouble()
            }
            totalSpread += sqrt(spreadNum / totalFrameMag)

            // 4. Spectral Roll-off (85% energy point)
            val rolloffThreshold = 0.85 * totalFrameMag
            var cumulativeMag = 0.0
            var rolloffBin = 0
            for (k in 0 until numBins) {
                cumulativeMag += mag[k]
                if (cumulativeMag >= rolloffThreshold) {
                    rolloffBin = k
                    break
                }
            }
            totalRolloff += rolloffBin.toDouble()

            // 5. Spectral Flatness & Crest
            var logSum = 0.0
            var maxMag = 0.0f
            for (k in 1 until numBins) {
                val m = mag[k].coerceAtLeast(1e-4f)
                logSum += ln(m.toDouble())
                if (mag[k] > maxMag) maxMag = mag[k]
            }
            val geometricMean = kotlin.math.exp(logSum / (numBins - 1))
            val arithmeticMean = totalFrameMag / numBins
            totalFlatness += (geometricMean / (arithmeticMean + 1e-6))
            totalCrest += (maxMag / (arithmeticMean + 1e-6))

            // 6. Sub-band Energy Ratios
            // Low (< 300Hz ~ bin 0..9), Mid/Speech (300Hz-3400Hz ~ bin 10..108), High (>3400Hz ~ bin 109..255)
            var lowMag = 0.0
            var midMag = 0.0
            var highMag = 0.0
            for (k in 0 until numBins) {
                when {
                    k <= 9 -> lowMag += mag[k]
                    k <= 108 -> midMag += mag[k]
                    else -> highMag += mag[k]
                }
            }
            totalLowEnergy += (lowMag / totalFrameMag)
            totalSpeechEnergy += (midMag / totalFrameMag)
            totalHighEnergy += (highMag / totalFrameMag)

            // 7. Spectral Flux
            if (f > 0) {
                var fluxSum = 0.0
                for (k in 0 until numBins) {
                    val diff = mag[k] - prevSpectrum[k]
                    fluxSum += diff * diff
                }
                totalFlux += sqrt(fluxSum)
            }
            System.arraycopy(mag, 0, prevSpectrum, 0, numBins)
        }

        // Fill features 0..31: Spectral Band Energies
        val divisor = validFrames.coerceAtLeast(1)
        for (b in 0 until NUM_SPECTRAL_BANDS) {
            features[b] = avgBandEnergies[b] / divisor
        }

        // Fill features 32..47: Spectral Shape & Distribution (16 features)
        features[32] = (totalCentroid / divisor).toFloat()
        features[33] = (totalSpread / divisor).toFloat()
        features[34] = (totalRolloff / divisor).toFloat()
        features[35] = (totalFlatness / divisor).toFloat()
        features[36] = (totalCrest / divisor).toFloat()
        features[37] = (totalLowEnergy / divisor).toFloat()
        features[38] = (totalSpeechEnergy / divisor).toFloat()
        features[39] = (totalHighEnergy / divisor).toFloat()
        features[40] = (totalFlux / divisor).toFloat()

        // Additional shape metrics
        features[41] = features[32] / (features[33] + 1f) // Centroid to spread ratio
        features[42] = features[38] / (features[37] + 0.01f) // Speech to low ratio
        features[43] = features[39] / (features[38] + 0.01f) // High to speech ratio
        features[44] = (features[32] / 128f)
        features[45] = (features[34] / 128f)
        features[46] = features[35] * 10f
        features[47] = features[36] * 0.1f

        // Fill features 48..63: Temporal & Dynamics (16 features)
        val overallRms = calculateRms(buffer)
        features[48] = ln(1.0 + overallRms).toFloat()
        features[49] = calculateZeroCrossingRate(buffer, 0, buffer.size)

        // Temporal chunk RMS across 8 sections
        val chunk8Size = buffer.size / 8
        for (c in 0 until 8) {
            val start = c * chunk8Size
            val end = if (c == 7) buffer.size else (c + 1) * chunk8Size
            val chunkRms = calculateRms(buffer, start, end)
            features[50 + c] = ln(1.0 + chunkRms).toFloat()
        }

        // Temporal chunk ZCR across 4 sections
        val chunk4Size = buffer.size / 4
        for (c in 0 until 4) {
            val start = c * chunk4Size
            val end = if (c == 3) buffer.size else (c + 1) * chunk4Size
            features[58 + c] = calculateZeroCrossingRate(buffer, start, end)
        }

        // Relative dynamic range and peak-to-RMS
        var maxAbs = 0
        for (s in buffer) {
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > maxAbs) maxAbs = absVal
        }
        features[62] = (maxAbs / (overallRms + 1.0)).toFloat() // Crest factor
        features[63] = (features[50] - features[57]) // Energy onset vs decay slope

        return features
    }

    private fun extractFallbackTimeFeatures(buffer: ShortArray): FloatArray {
        val features = FloatArray(FEATURE_DIM)
        val chunkSize = (buffer.size / FEATURE_DIM).coerceAtLeast(1)
        for (i in 0 until FEATURE_DIM) {
            val start = i * chunkSize
            val end = ((i + 1) * chunkSize).coerceAtMost(buffer.size)
            if (start < end) {
                val rms = calculateRms(buffer, start, end)
                val zcr = calculateZeroCrossingRate(buffer, start, end)
                features[i] = (rms + zcr * 100.0).toFloat()
            }
        }
        return features
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT. Length of real and imag must be a power of 2.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterfly computations
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (m in 0 until halfLen) {
                    val uR = real[i + m]
                    val uI = imag[i + m]
                    val vR = real[i + m + halfLen] * wR - imag[i + m + halfLen] * wI
                    val vI = real[i + m + halfLen] * wI + imag[i + m + halfLen] * wR
                    real[i + m] = uR + vR
                    imag[i + m] = uI + vI
                    real[i + m + halfLen] = uR - vR
                    imag[i + m + halfLen] = uI - vI
                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun clearEnrollment() {
        prefs.edit().clear().apply()
        isEnrolled = false
    }

    companion object {
        private const val TAG = "VoiceBiometric"
        const val FEATURE_DIM = 64
        private const val NUM_SPECTRAL_BANDS = 32
        const val MATCH_THRESHOLD = 0.75f // Cosine similarity threshold for user's voice (increased from 0.68)
        const val MIN_AUDIO_ENERGY_RMS = 250.0 // Minimum audio energy check to reject silence and ambient noise
        private const val KEY_IS_ENROLLED = "key_is_enrolled"
        private const val KEY_VOICE_TEMPLATE = "key_voice_template"

        fun calculateRms(buffer: ShortArray, start: Int = 0, end: Int = buffer.size): Double {
            val length = end - start
            if (length <= 0) return 0.0
            var sumSquares = 0.0
            for (i in start until end) {
                val sample = buffer[i].toDouble()
                sumSquares += sample * sample
            }
            return sqrt(sumSquares / length)
        }

        fun calculateZeroCrossingRate(buffer: ShortArray, start: Int = 0, end: Int = buffer.size): Float {
            val length = end - start
            if (length <= 1) return 0f
            var crossings = 0
            for (j in (start + 1) until end) {
                if ((buffer[j] >= 0 && buffer[j - 1] < 0) || (buffer[j] < 0 && buffer[j - 1] >= 0)) {
                    crossings++
                }
            }
            return crossings.toFloat() / (length - 1)
        }

        fun l2Norm(vec: FloatArray): Float {
            var sum = 0f
            for (v in vec) sum += v * v
            return sqrt(sum)
        }
    }
}
