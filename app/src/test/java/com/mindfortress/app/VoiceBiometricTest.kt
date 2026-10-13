package com.mindfortress.app

import com.mindfortress.app.ai.HandsFreeVoiceService
import com.mindfortress.app.ai.VoiceBiometricManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VoiceBiometricTest {

    @Test
    fun testNavKeySerialization() {
        val s = kotlinx.serialization.serializer(JarvisHudKey::class.java)
        assertNotNull(s)
    }

    @Test
    fun calculateRms_silence_returnsZero() {
        val silence = ShortArray(100) { 0 }
        val rms = VoiceBiometricManager.calculateRms(silence)
        assertEquals(0.0, rms, 0.001)
    }

    @Test
    fun calculateRms_constantAmplitude_returnsAmplitude() {
        val amplitude: Short = 1000
        val buffer = ShortArray(100) { amplitude }
        val rms = VoiceBiometricManager.calculateRms(buffer)
        assertEquals(1000.0, rms, 0.001)
    }

    @Test
    fun calculateZeroCrossingRate_constantSignal_returnsZero() {
        val buffer = ShortArray(100) { 500 }
        val zcr = VoiceBiometricManager.calculateZeroCrossingRate(buffer)
        assertEquals(0f, zcr, 0.001f)
    }

    @Test
    fun calculateZeroCrossingRate_alternatingSignal_detectsCrossings() {
        val buffer = ShortArray(10) { i -> if (i % 2 == 0) 500 else -500 }
        val zcr = VoiceBiometricManager.calculateZeroCrossingRate(buffer)
        assertEquals(1.0f, zcr, 0.001f)
    }

    @Test
    fun l2Norm_unitVector_returnsExpectedMagnitude() {
        val vec = floatArrayOf(3f, 4f)
        val norm = VoiceBiometricManager.l2Norm(vec)
        assertEquals(5.0f, norm, 0.001f)
    }

    @Test
    fun translateHinglish_phoneLockCommands_translatedToLock() {
        assertEquals("lock my phone for 60 minutes", HandsFreeVoiceService.translateHinglish("phone lock kar do"))
        assertEquals("lock my phone for 60 minutes", HandsFreeVoiceService.translateHinglish("phone lock karo"))
        assertEquals("lock my phone for 60 minutes", HandsFreeVoiceService.translateHinglish("phone lock kardo"))
    }

    @Test
    fun translateHinglish_studyAndMonkModeCommands_translatedToMonkMode() {
        assertEquals("monk mode", HandsFreeVoiceService.translateHinglish("padhai shuru karo"))
        assertEquals("monk mode", HandsFreeVoiceService.translateHinglish("study mode lagao"))
    }

    @Test
    fun translateHinglish_sunRaheHo_translatedToAreYouListening() {
        assertEquals("are you listening", HandsFreeVoiceService.translateHinglish("sun rahe ho"))
        assertEquals("are you listening", HandsFreeVoiceService.translateHinglish("sun rahi ho"))
    }

    @Test
    fun translateHinglish_distractionRoko_translatedToClearPhone() {
        assertEquals("clear phone", HandsFreeVoiceService.translateHinglish("distraction roko"))
        assertEquals("clear phone", HandsFreeVoiceService.translateHinglish("distraction band karo"))
    }

    @Test
    fun translateHinglish_feynmanTestLo_translatedToFeynman() {
        assertEquals("feynman", HandsFreeVoiceService.translateHinglish("feynman test lo"))
    }

    @Test
    fun processSpokenCommand_addressesJarvis_isAccepted() {
        val result = HandsFreeVoiceService.processSpokenCommand("Hey Jarvis what is quantum mechanics")
        assertNotNull(result)
        assertTrue(result!!.contains("quantum mechanics"))

        val result2 = HandsFreeVoiceService.processSpokenCommand("jarvis lock phone for 45 minutes")
        assertNotNull(result2)
        assertTrue(result2!!.contains("lock phone for 45 minutes"))
    }

    @Test
    fun processSpokenCommand_hinglishCommand_isAcceptedAndTranslated() {
        val result = HandsFreeVoiceService.processSpokenCommand("phone lock kar do")
        assertEquals("lock my phone for 60 minutes", result)

        val result2 = HandsFreeVoiceService.processSpokenCommand("padhai shuru karo")
        assertEquals("monk mode", result2)
    }

    @Test
    fun processSpokenCommand_directActionCommand_isAccepted() {
        val result = HandsFreeVoiceService.processSpokenCommand("open youtube")
        assertEquals("open youtube", result)

        val result2 = HandsFreeVoiceService.processSpokenCommand("monk mode")
        assertEquals("monk mode", result2)
    }

    @Test
    fun processSpokenCommand_pureBackgroundConversation_isIgnored() {
        // Pure ambient conversation not addressing the assistant
        val result1 = HandsFreeVoiceService.processSpokenCommand("what do you want for dinner tonight")
        assertNull(result1)

        val result2 = HandsFreeVoiceService.processSpokenCommand("the weather is quite nice today")
        assertNull(result2)

        val result3 = HandsFreeVoiceService.processSpokenCommand("i will call him back later")
        assertNull(result3)
    }
}
