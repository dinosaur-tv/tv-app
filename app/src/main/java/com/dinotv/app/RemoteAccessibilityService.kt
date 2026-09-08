package com.dinotv.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.graphics.Rect

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

    fun press(key: String): Boolean = when (key) {
        "back" -> injectKey(KeyEvent.KEYCODE_BACK) || performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> injectKey(KeyEvent.KEYCODE_HOME) || performGlobalAction(GLOBAL_ACTION_HOME) || openHome()
        "ok" -> injectKey(KeyEvent.KEYCODE_DPAD_CENTER) || clickFocused() || tapCenter()
        "up" -> injectKey(KeyEvent.KEYCODE_DPAD_UP) || step(View.FOCUS_UP)
        "down" -> injectKey(KeyEvent.KEYCODE_DPAD_DOWN) || step(View.FOCUS_DOWN)
        "left" -> injectKey(KeyEvent.KEYCODE_DPAD_LEFT) || step(View.FOCUS_LEFT)
        "right" -> injectKey(KeyEvent.KEYCODE_DPAD_RIGHT) || step(View.FOCUS_RIGHT)
        else -> false
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
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, InputDevice.SOURCE_DPAD,
            )
            val up = KeyEvent(
                now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, InputDevice.SOURCE_DPAD,
            )
            val okDown = inject.invoke(input, down, 0) as? Boolean ?: false
            val okUp = inject.invoke(input, up, 0) as? Boolean ?: false
            okDown && okUp
        } catch (_: Exception) {
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
    } catch (_: Exception) {
        rootInActiveWindow
    }

    private fun focusedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: selectedNode(root)

    private fun selectedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        var i = 0
        while (i < queue.size) {
            val node = queue[i++]
            if (node.isSelected && node.isVisibleToUser) return node
            for (c in 0 until node.childCount) node.getChild(c)?.let(queue::add)
        }
        return null
    }

    private fun clickFocused(): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root) ?: return false
        if (focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = focused.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
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
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectClickable(root, nodes)
        val target = nodes
            .asSequence()
            .filter { it !== focused }
            .map { it to Rect().also { box -> it.getBoundsInScreen(box) } }
            .filter { (_, box) -> !box.isEmpty }
            .filter { (_, box) -> inDirection(origin, box, direction) }
            .minByOrNull { (_, box) -> score(origin, box, direction) }
            ?.first
            ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            tap(target)
    }

    private fun collectClickable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser && (node.isClickable || node.isFocusable || node.isCheckable)) out += node
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectClickable(it, out) }
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

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        @Volatile
        private var instance: RemoteAccessibilityService? = null

        fun press(key: String): Boolean = instance?.press(key) ?: false

        fun connected(): Boolean = instance != null
    }
}
