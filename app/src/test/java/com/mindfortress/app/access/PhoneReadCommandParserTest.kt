package com.mindfortress.app.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneReadCommandParserTest {
    @Test
    fun parsesLatestWhatsAppMessage() {
        assertEquals(
            PhoneReadIntent.LATEST_WHATSAPP_MESSAGE,
            PhoneReadCommandParser.parse("Hey Jarvis, read my latest WhatsApp message")
        )
    }

    @Test
    fun parsesLatestNotification() {
        assertEquals(
            PhoneReadIntent.LATEST_NOTIFICATION,
            PhoneReadCommandParser.parse("Read my latest notification")
        )
    }

    @Test
    fun parsesRecentNotifications() {
        assertEquals(
            PhoneReadIntent.RECENT_NOTIFICATIONS,
            PhoneReadCommandParser.parse("Read all my notifications")
        )
    }

    @Test
    fun parsesScreenRead() {
        assertEquals(
            PhoneReadIntent.CURRENT_SCREEN,
            PhoneReadCommandParser.parse("Read everything on this screen")
        )
    }

    @Test
    fun ordinaryQuestionsDoNotTriggerPhoneReaders() {
        assertNull(PhoneReadCommandParser.parse("What is a notification listener?"))
        assertNull(PhoneReadCommandParser.parse("Explain accessibility services"))
        assertNull(PhoneReadCommandParser.parse("Hello Jarvis"))
    }
}
