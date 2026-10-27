package com.dinotv.app

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Key presses through the television's own adbd. Android 11 lets an ordinary app neither
 * inject keys nor reach the Kinopoisk music player another way: it has no media session,
 * ignores media keys and listens only to the D-pad of the window in focus. Network
 * debugging is on, and `input keyevent` run as shell lands exactly like a button on the
 * physical remote. The first connection shows Android's «Allow debugging?» prompt once;
 * after «Always allow» it stays silent.
 */
object TvShell {
    private const val TAG = "TvShell"
    private const val HOST = "127.0.0.1"
    private const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 2_000
    // Long enough for someone to answer the debugging prompt with the physical remote.
    private const val READ_TIMEOUT_MS = 60_000

    private val worker = Executors.newSingleThreadExecutor { Thread(it, TAG) }
    private var dadb: Dadb? = null
    private val warming = AtomicBoolean(false)

    /** The television has accepted this app's key at least once since it started. */
    @Volatile
    var ready = false
        private set

    /** Runs [task] off the main thread, one at a time, so keys arrive in order. */
    fun later(task: () -> Unit) {
        worker.execute {
            try {
                task()
            } catch (error: Throwable) {
                Log.w(TAG, "task failed", error)
            }
        }
    }

    /** Asks for the connection once in the background; the television shows its prompt. */
    fun warmUp(context: Context) {
        if (ready || !warming.compareAndSet(false, true)) return
        val app = context.applicationContext
        later {
            try {
                keyevent(app, KeyEvent.KEYCODE_UNKNOWN)
            } finally {
                warming.set(false)
            }
        }
    }

    /** Blocking: never call it on the main thread. */
    @Synchronized
    fun keyevent(context: Context, keyCode: Int): Boolean {
        repeat(2) { attempt ->
            try {
                val response = connection(context).shell("input keyevent $keyCode")
                ready = true
                if (response.exitCode == 0) return true
                Log.w(TAG, "input keyevent $keyCode exit=${response.exitCode} ${response.allOutput.trim()}")
                return false
            } catch (error: Throwable) {
                // adbd restarts drop the socket; one fresh connection is worth a try.
                Log.w(TAG, "keyevent $keyCode attempt $attempt failed: ${error.message}")
                close()
            }
        }
        return false
    }

    private fun connection(context: Context): Dadb =
        dadb ?: Dadb.create(HOST, PORT, keyPair(context), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS).also { dadb = it }

    private fun close() {
        try {
            dadb?.close()
        } catch (_: Throwable) {
            // Already gone.
        }
        dadb = null
    }

    private fun keyPair(context: Context): AdbKeyPair {
        val dir = File(context.noBackupFilesDir, "adb")
        val private = File(dir, "adbkey")
        val public = File(dir, "adbkey.pub")
        if (!private.exists() || !public.exists()) {
            AdbKeyPair.generate(private, public)
            // The prompt on the television names the key by this comment.
            public.writeText(public.readText().replace(" unknown@unknown", " Dino TV"))
        }
        return AdbKeyPair.read(private, public)
    }
}
