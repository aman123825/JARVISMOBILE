package com.mindfortress.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionFirstActionBrokerTest {

    @Test
    fun openAppIsClassifiedAsLowRiskNavigation() {
        val proposal = PermissionFirstActionBroker.propose("open WhatsApp")

        assertEquals(DeviceActionType.OPEN_APP, proposal?.type)
        assertEquals("WhatsApp", proposal?.target)
        assertTrue(proposal?.explanation?.contains("without a second confirmation") == true)
    }

    @Test
    fun focusLockRequiresApproval() {
        val proposal = PermissionFirstActionBroker.propose(
            "Start 90 minute ultradian deep work block on STEM"
        )

        assertEquals(DeviceActionType.FOCUS_LOCK, proposal?.type)
        assertTrue(proposal?.explanation?.contains("Nothing changes unless you approve") == true)
    }

    @Test
    fun informationalDeepWorkQuestionDoesNotBecomeLockAction() {
        assertNull(PermissionFirstActionBroker.propose("Explain what deep work means"))
        assertEquals(false, PermissionFirstActionBroker.isFocusLockCommand("Explain what deep work means"))
    }

    @Test
    fun reminderOpensCalendarDraftOnly() {
        val proposal = PermissionFirstActionBroker.propose(
            "Remind me to submit the assignment tomorrow at 9am"
        )

        assertEquals(DeviceActionType.CALENDAR_REMINDER, proposal?.type)
        assertTrue(proposal?.explanation?.contains("No reminder is saved") == true)
    }

    @Test
    fun messageIsDraftNotSent() {
        val proposal = PermissionFirstActionBroker.propose(
            "Send Rahul a message saying I will be 10 minutes late"
        )

        assertEquals(DeviceActionType.DRAFT_SMS, proposal?.type)
        assertEquals("Rahul", proposal?.target)
        assertEquals("I will be 10 minutes late", proposal?.messageBody)
        assertTrue(proposal?.explanation?.contains("Nothing is sent automatically") == true)
    }

    @Test
    fun ordinaryQuestionIsNotAnAction() {
        assertNull(PermissionFirstActionBroker.propose("Explain floating point arithmetic"))
    }
}
