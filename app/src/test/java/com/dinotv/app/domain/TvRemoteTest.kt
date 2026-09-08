package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvRemoteTest {
    @Test
    fun `queues a Kinopoisk launch`() {
        val command = TvRemote.take("launch", "t1", app = "kinopoisk")
        assertEquals("launch", command?.action)
        assertEquals("kinopoisk", command?.app)
    }

    @Test
    fun `queues a d-pad key`() {
        assertEquals("ok", TvRemote.take("key", "t1", key = "ok")?.key)
    }

    @Test
    fun `ignores unknown apps and keys`() {
        assertNull(TvRemote.take("launch", "t1", app = "netflix"))
        assertNull(TvRemote.take("key", "t1", key = "power"))
        assertNull(TvRemote.take("key", "", key = "ok"))
    }
}
