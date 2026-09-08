package com.dinotv.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.View
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
            "back" -> injectKey(KeyEvent.KEYCODE_BACK) || performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME) || openHome()
            "ok" -> injectKey(KeyEvent.KEYCODE_DPAD_CENTER) || clickFocused() || tapCenter()
            "up" -> injectKey(KeyEvent.KEYCODE_DPAD_UP) || step(View.FOCUS_UP)
            "down" -> injectKey(KeyEvent.KEYCODE_DPAD_DOWN) || step(View.FOCUS_DOWN)
            "left" -> injectKey(KeyEvent.KEYCODE_DPAD_LEFT) || step(View.FOCUS_LEFT)
            "right" -> injectKey(KeyEvent.KEYCODE_DPAD_RIGHT) || step(View.FOCUS_RIGHT)
            else -> false
        }
    } catch (error: Throwable) {
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
            val okDown = inject.invoke(input, down, 0) as? Boolean ?: false
            val okUp = inject.invoke(input, up, 0) as? Boolean ?: false
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
        windows?.firstOrNull { it.isActive }?.root ?: rootInActiveWindow
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
        return false
    }

    private fun step(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root)
        if (focused != null) {
            focused.focusSearch(direction)?.let { next ->
                if (next.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                    next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
                    next.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
            }
        }
        val origin = Rect()
        if (focused != null) focused.getBoundsInScreen(origin) else origin.set(80, 540, 81, 541)
        val target = walk(root)
            .asSequence()
            .filter { it !== focused && it.isVisibleToUser && (it.isClickable || it.isFocusable) }
            .map { it to Rect().also { box -> it.getBoundsInScreen(box) } }
            .filter { (_, box) -> !box.isEmpty && box.width() < 900 && box.height() < 700 }
            .filter { (_, box) -> inDirection(origin, box, direction) }
            .minByOrNull { (_, box) -> score(origin, box, direction) }
            ?.first
            ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            tap(target)
    }

    private fun walk(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out += node
            val childCount = node.childCount.coerceAtMost(40)
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

    private fun tapCenter(): Boolean = tapAt(960f, 540f)

    private fun tapAt(x: Float, y: Float): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    } catch (_: Throwable) {
        false
    }

    companion object {
        private const val MAX_NODES = 120

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
