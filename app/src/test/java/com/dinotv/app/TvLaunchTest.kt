package com.dinotv.app

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLaunchTest {
    @Test
    fun `launch flags can raise an existing task over another app`() {
        assertTrue(TvLaunch.FLAGS and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(TvLaunch.FLAGS and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(TvLaunch.FLAGS and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
            TvLaunch.FLAGS,
        )
    }
}
