package com.dinotv.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun press(key: String): Boolean = try {
        when (key) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK) || injectKey(KeyEvent.KEYCODE_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME) || openHome()
            "ok" -> injectKey(KeyEvent.KEYCODE_DPAD_CENTER) || clickFocused() || tapCenter()
            "up" -> injectKey(KeyEvent.KEYCODE_DPAD_UP) || step(View.FOCUS_UP) || swipe(0f, 1f)
            "down" -> injectKey(KeyEvent.KEYCODE_DPAD_DOWN) || step(View.FOCUS_DOWN) || swipe(0f, -1f)
            "left" -> injectKey(KeyEvent.KEYCODE_DPAD_LEFT) || step(View.FOCUS_LEFT) || swipe(1f, 0f)
            "right" -> injectKey(KeyEvent.KEYCODE_DPAD_RIGHT) || step(View.FOCUS_RIGHT) || swipe(-1f, 0f)
            else -> false
        }
    } catch (_: Throwable) {
        // Never let a pad key take down the whole Dino process.
        false
    }

    private fun injectKey(keyCode: Int): Boolean {
        return try {
            val input = getSystemService(INPUT_SERVICE) as InputManager
            val inject = InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                -1, 0, 0, InputDevice.SOURCE_DPAD,
            )
            val up = KeyEvent(
                now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                -1, 0, 0, InputDevice.SOURCE_DPAD,
            )
            // Mode 2 = INJECT_INPUT_EVENT_MODE_ASYNC — more likely to be accepted.
            val okDown = inject.invoke(input, down, 2) as? Boolean ?: false
            val okUp = inject.invoke(input, up, 2) as? Boolean ?: false
            okDown && okUp
        } catch (_: Throwable) {
            false
        }
    }

    private fun openHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(home)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun activeRoot(): AccessibilityNodeInfo? = try {
        windows?.firstOrNull { it.isActive }?.root
            ?: windows?.maxByOrNull { it.layer }?.root
            ?: rootInActiveWindow
    } catch (_: Throwable) {
        rootInActiveWindow
    }

    private fun focusedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: selectedNode(root)

    private fun selectedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (node in walk(root)) {
            if (node.isSelected && node.isVisibleToUser) return node
        }
        return null
    }

    private fun clickFocused(): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root) ?: return false
        if (focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = focused.parent
        var hops = 0
        while (parent != null && hops < 8) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
            hops += 1
        }
        return tap(focused)
    }

    private fun step(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root)
        if (focused != null) {
            focused.focusSearch(direction)?.let { next ->
                if (next.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                    next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
                    next.performAction(AccessibilityNodeInfo.ACTION_SELECT) ||
                    next.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
            }
        }
        val metrics = screenSize()
        val origin = Rect()
        if (focused != null) focused.getBoundsInScreen(origin) else {
            origin.set(metrics.widthPixels / 2, metrics.heightPixels / 2, metrics.widthPixels / 2 + 1, metrics.heightPixels / 2 + 1)
        }
        val target = walk(root)
            .asSequence()
            .filter { it !== focused && it.isVisibleToUser && (it.isClickable || it.isFocusable || it.isSelected) }
            .map { it to Rect().also { box -> it.getBoundsInScreen(box) } }
            .filter { (_, box) -> !box.isEmpty && box.width() in 24 until metrics.widthPixels && box.height() in 24 until metrics.heightPixels }
            .filter { (_, box) -> inDirection(origin, box, direction) }
            .minByOrNull { (_, box) -> score(origin, box, direction) }
            ?.first
            ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_SELECT) ||
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            tap(target)
    }

    private fun walk(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(96)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out += node
            val childCount = node.childCount.coerceAtMost(48)
            for (i in 0 until childCount) {
                if (out.size + queue.size >= MAX_NODES) break
                node.getChild(i)?.let(queue::add)
            }
        }
        return out
    }

    private fun inDirection(from: Rect, to: Rect, direction: Int): Boolean {
        val dx = to.centerX() - from.centerX()
        val dy = to.centerY() - from.centerY()
        return when (direction) {
            View.FOCUS_UP -> dy < -12 && kotlin.math.abs(dy) >= kotlin.math.abs(dx) / 2
            View.FOCUS_DOWN -> dy > 12 && kotlin.math.abs(dy) >= kotlin.math.abs(dx) / 2
            View.FOCUS_LEFT -> dx < -12 && kotlin.math.abs(dx) >= kotlin.math.abs(dy) / 2
            View.FOCUS_RIGHT -> dx > 12 && kotlin.math.abs(dx) >= kotlin.math.abs(dy) / 2
            else -> false
        }
    }

    private fun score(from: Rect, to: Rect, direction: Int): Int {
        val dx = kotlin.math.abs(to.centerX() - from.centerX())
        val dy = kotlin.math.abs(to.centerY() - from.centerY())
        return when (direction) {
            View.FOCUS_UP, View.FOCUS_DOWN -> dy * 3 + dx
            else -> dx * 3 + dy
        }
    }

    private fun tap(node: AccessibilityNodeInfo): Boolean {
        val box = Rect().also { node.getBoundsInScreen(it) }
        if (box.isEmpty) return false
        return tapAt(box.centerX().toFloat(), box.centerY().toFloat())
    }

    private fun tapCenter(): Boolean {
        val metrics = screenSize()
        return tapAt(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
    }

    private fun swipe(dx: Float, dy: Float): Boolean {
        val metrics = screenSize()
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val distance = minOf(metrics.widthPixels, metrics.heightPixels) * 0.18f
        return swipeFrom(cx + dx * distance, cy + dy * distance, cx - dx * distance, cy - dy * distance)
    }

    private fun swipeFrom(x1: Float, y1: Float, x2: Float, y2: Float): Boolean = try {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 120)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    } catch (_: Throwable) {
        false
    }

    private fun tapAt(x: Float, y: Float): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    } catch (_: Throwable) {
        false
    }

    private fun screenSize(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0) metrics.widthPixels = 1920
        if (metrics.heightPixels <= 0) metrics.heightPixels = 1080
        return metrics
    }

    companion object {
        private const val MAX_NODES = 180

        @Volatile
        private var instance: RemoteAccessibilityService? = null

        fun press(key: String): Boolean = try {
            instance?.press(key) ?: false
        } catch (_: Throwable) {
            false
        }

        fun connected(): Boolean = instance != null
    }
}
