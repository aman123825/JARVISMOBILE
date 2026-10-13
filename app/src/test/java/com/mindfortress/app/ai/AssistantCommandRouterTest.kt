package com.mindfortress.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCommandRouterTest {

    @Test
    fun parsesOpenAppAndWebCommands() {
        assertEquals(AssistantCommand.OpenApp("WhatsApp"), AssistantCommandRouter.parse("Hey Jarvis, open WhatsApp"))
        assertEquals(
            AssistantCommand.OpenWebsite("https://example.com"),
            AssistantCommandRouter.parse("Open example.com")
        )
        assertEquals(
            AssistantCommand.SearchWeb("How do heat pumps work?"),
            AssistantCommandRouter.parse("Search the web for How do heat pumps work?")
        )
        assertEquals(
            AssistantCommand.NavigateTo("IIT Bombay"),
            AssistantCommandRouter.parse("take me to IIT Bombay")
        )
    }

    @Test
    fun parsesAlarmTimesAndRejectsInvalidTime() {
        assertEquals(
            AssistantCommand.SetAlarm(7, 30, "JARVIS alarm"),
            AssistantCommandRouter.parse("set an alarm for 7:30 am")
        )
        assertEquals(
            AssistantCommand.SetAlarm(19, 5, "JARVIS alarm"),
            AssistantCommandRouter.parse("wake me up at 7:05 pm")
        )
        assertNull(AssistantCommandRouter.parse("set alarm for 26:99"))
    }

    @Test
    fun parsesTimersAndReminders() {
        assertEquals(
            AssistantCommand.SetTimer(600, "JARVIS timer"),
            AssistantCommandRouter.parse("set a timer for 10 minutes")
        )
        assertEquals(
            AssistantCommand.SetTimer(90, "Tea"),
            AssistantCommandRouter.parse("timer for 90 seconds called Tea")
        )
        assertEquals(
            AssistantCommand.CreateReminder(1200, "stretch"),
            AssistantCommandRouter.parse("remind me in 20 minutes to stretch")
        )
    }

    @Test
    fun communicationCommandsNeverRepresentImmediateSending() {
        assertEquals(
            AssistantCommand.DialNumber("+919876543210"),
            AssistantCommandRouter.parse("call +91 98765 43210")
        )
        assertEquals(
            AssistantCommand.ComposeSms("+919876543210", "I'm running late"),
            AssistantCommandRouter.parse("text +91 98765 43210 saying I'm running late")
        )
    }

    @Test
    fun parsesPhoneSettingsAndPrivateMemoryCommands() {
        assertEquals(
            AssistantCommand.OpenSettings("wifi"),
            AssistantCommandRouter.parse("open Wi-Fi settings")
        )
        assertEquals(
            AssistantCommand.Remember("my project is called ShambaLens"),
            AssistantCommandRouter.parse("remember that my project is called ShambaLens")
        )
        assertEquals(
            AssistantCommand.AddTask("finish the lab report"),
            AssistantCommandRouter.parse("add task finish the lab report")
        )
        assertEquals(
            AssistantCommand.CompleteTask("lab report"),
            AssistantCommandRouter.parse("complete task lab report")
        )
        assertEquals(AssistantCommand.ListTasks, AssistantCommandRouter.parse("show tasks"))
        assertEquals(AssistantCommand.ListMemories, AssistantCommandRouter.parse("what do you remember"))
    }

    @Test
    fun doesNotTurnOrdinaryConversationIntoDeviceAction() {
        assertNull(AssistantCommandRouter.parse("I think I should call my professor tomorrow"))
        assertNull(AssistantCommandRouter.parse("Could you explain the second law of thermodynamics?"))
        assertNull(AssistantCommandRouter.parse("turn off all the safeguards"))
    }

    @Test
    fun parsesFocusVolumeAndExplicitResearchCommands() {
        assertEquals(
            AssistantCommand.EngageFocusMode(90),
            AssistantCommandRouter.parse("Hey Jarvis, start deep focus for 90 minutes")
        )
        assertEquals(
            AssistantCommand.EngageFocusMode(60),
            AssistantCommandRouter.parse("lock my phone")
        )
        assertEquals(
            AssistantCommand.ClearDistractions,
            AssistantCommandRouter.parse("clear distractions")
        )
        assertEquals(
            AssistantCommand.SetVolume(35),
            AssistantCommandRouter.parse("set volume to 35%")
        )
        assertEquals(
            AssistantCommand.ResearchOnline("edge AI for crop disease detection"),
            AssistantCommandRouter.parse("research online edge AI for crop disease detection")
        )
        assertNull(AssistantCommandRouter.parse("research papers are difficult to read"))
    }

    @Test
    fun parsesLocalDeviceStatusRequests() {
        assertEquals(AssistantCommand.GetCurrentTime, AssistantCommandRouter.parse("what time is it"))
        assertEquals(AssistantCommand.GetBatteryStatus, AssistantCommandRouter.parse("battery percentage"))
        assertEquals(AssistantCommand.GetVolumeStatus, AssistantCommandRouter.parse("current volume"))
    }

    @Test
    fun parsesOptInNotificationCommands() {
        assertEquals(
            AssistantCommand.ListNotifications,
            AssistantCommandRouter.parse("read my notifications")
        )
        assertEquals(
            AssistantCommand.SummarizeNotifications,
            AssistantCommandRouter.parse("summarize my notifications")
        )
        assertEquals(
            AssistantCommand.ClearNotificationHistory,
            AssistantCommandRouter.parse("clear notification history")
        )
        assertEquals(
            AssistantCommand.OpenSettings("notifications"),
            AssistantCommandRouter.parse("open notification access settings")
        )
    }

    @Test
    fun helpListsSupportedToolset() {
        assertEquals(AssistantCommand.Help, AssistantCommandRouter.parse("what can you do"))
        assertTrue(AssistantCommandRouter.parse("open browser") is AssistantCommand.OpenApp)
    }

    @Test
    fun parsesCanonicalCommandsSuitableForModelNormalisation() {
        val canonicalCommands = listOf(
            "open WhatsApp",
            "search the web for numerical methods",
            "research online drought forecasting",
            "navigate to IIT Bombay",
            "set alarm for 7:30 am",
            "timer for 10 minutes",
            "remind me in 15 minutes to drink water",
            "set volume to 35%",
            "start deep focus for 30 minutes",
            "clear distractions",
            "what time is it",
            "what is my battery",
            "read notifications",
            "summarize my notifications",
            "remember that buy M3 screws",
            "add task finish lab report",
            "show tasks",
            "open Wi-Fi settings",
            "call +15555550101",
            "text +15555550102 saying I will arrive soon"
        )
        canonicalCommands.forEach { command ->
            assertTrue("Router did not recognise canonical command: $command", AssistantCommandRouter.parse(command) != null)
        }
    }
}
