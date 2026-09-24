package com.dinotv.app

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingRoomOverlayTest {
    @Test
    fun opaqueOverlayKeepsTheScreenAwake() {
        assertTrue(LivingRoomOverlay.WINDOW_FLAGS and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test
    fun overlayTakesFocusSoThePhysicalRemoteCanLeave() {
        assertEquals(0, LivingRoomOverlay.WINDOW_FLAGS and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertEquals(0, LivingRoomOverlay.WINDOW_FLAGS and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
}
