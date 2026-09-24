package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDismissTest {
    @Test
    fun `active only while the grace window is still open`() {
        assertFalse(UserDismiss.isActive(untilEpochMs = 0L, nowEpochMs = 1_000L))
        assertTrue(UserDismiss.isActive(untilEpochMs = 2_000L, nowEpochMs = 1_000L))
        assertFalse(UserDismiss.isActive(untilEpochMs = 1_000L, nowEpochMs = 1_000L))
        assertFalse(UserDismiss.isActive(untilEpochMs = 900L, nowEpochMs = 1_000L))
    }

    @Test
    fun `until stamps a grace window from now`() {
        assertEquals(1_000L + UserDismiss.GRACE_MS, UserDismiss.until(1_000L))
        assertEquals(1_500L, UserDismiss.until(1_000L, graceMs = 500L))
    }
}
