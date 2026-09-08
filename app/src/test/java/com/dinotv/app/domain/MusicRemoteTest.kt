package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicRemoteTest {
    @Test
    fun `reads a transport command for the television`() {
        val command = MusicRemote.take("toggle", "2026-09-08T12:00:00.000Z")
        assertEquals("toggle", command?.action)
        assertEquals("2026-09-08T12:00:00.000Z", command?.at)
        assertNull(command?.volume)
    }

    @Test
    fun `keeps a volume percent`() {
        assertEquals(20, MusicRemote.take("volume", "t1", 20)?.volume)
    }

    @Test
    fun `ignores unknown actions and empty stamps`() {
        assertNull(MusicRemote.take("toTv", "t1"))
        assertNull(MusicRemote.take("pause", ""))
        assertNull(MusicRemote.take("", "t1"))
    }
}
