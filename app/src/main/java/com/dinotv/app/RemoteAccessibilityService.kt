package com.dinotv.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Build
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
            "back" -> {
                RemoteCursor.hide()
                performGlobalAction(GLOBAL_ACTION_BACK) || injectKey(KeyEvent.KEYCODE_BACK)
            }
            "home" -> {
                RemoteCursor.hide()
                performGlobalAction(GLOBAL_ACTION_HOME) || openHome()
            }
            "ok" -> {
                if (RemoteCursor.visible) {
                    tapCursor() || injectKey(KeyEvent.KEYCODE_DPAD_CENTER) || clickFocused() || tapCenter()
                } else {
                    injectKey(KeyEvent.KEYCODE_DPAD_CENTER) || clickFocused() || clickVirtual() || tapCenter()
                }
            }
            "up" -> move(View.FOCUS_UP)
            "down" -> move(View.FOCUS_DOWN)
            "left" -> move(View.FOCUS_LEFT)
            "right" -> move(View.FOCUS_RIGHT)
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    private fun move(direction: Int): Boolean {
        // Only count a move as handled when a real focus change happened.
        // Gestures/scrolls often return true without moving Kinopoisk Compose focus.
        if (injectKey(directionKey(direction))) return true
        return stepFocus(direction)
    }

    private fun tapCursor(): Boolean {
        if (!RemoteCursor.visible) return false
        return tapAt(RemoteCursor.pointX, RemoteCursor.pointY)
    }

    private fun stepFocus(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root) ?: return false
        focused.focusSearch(direction)?.let { next ->
            if (next.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
                next.performAction(AccessibilityNodeInfo.ACTION_SELECT)
            ) {
                val box = Rect().also { next.getBoundsInScreen(it) }
                virtualFocusBox = Rect(box)
                return true
            }
        }
        return false
    }

    private fun directionKey(direction: Int): Int = when (direction) {
        View.FOCUS_UP -> KeyEvent.KEYCODE_DPAD_UP
        View.FOCUS_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        View.FOCUS_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        else -> KeyEvent.KEYCODE_DPAD_RIGHT
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
            if ((node.isSelected || node.isAccessibilityFocused) && node.isVisibleToUser) return node
        }
        return null
    }

    private fun clickFocused(): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root) ?: return false
        return activate(focused)
    }

    private fun activate(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var hops = 0
        while (parent != null && hops < 8) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
            hops += 1
        }
        return tap(node)
    }

    private fun clickVirtual(): Boolean {
        val box = virtualFocusBox ?: return false
        return tapAt(box.centerX().toFloat(), box.centerY().toFloat())
    }

    private fun step(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val candidates = focusCandidates(root)
        if (candidates.isEmpty()) return false
        val focused = focusedNode(root)
        val originBox = Rect()
        when {
            focused != null -> focused.getBoundsInScreen(originBox)
            virtualFocusBox != null -> originBox.set(virtualFocusBox!!)
            else -> {
                val metrics = screenSize()
                originBox.set(
                    metrics.widthPixels / 2,
                    metrics.heightPixels / 2,
                    metrics.widthPixels / 2 + 1,
                    metrics.heightPixels / 2 + 1,
                )
            }
        }
        val target = candidates
            .asSequence()
            .filter { it !== focused }
            .map { it to Rect().also { box -> it.getBoundsInScreen(box) } }
            .filter { (_, box) -> !box.isEmpty }
            .filter { (_, box) -> inDirection(originBox, box, direction) }
            .minByOrNull { (_, box) -> score(originBox, box, direction) }
            ?.first
            ?: return false
        val targetBox = Rect().also { target.getBoundsInScreen(it) }
        virtualFocusBox = Rect(targetBox)
        return target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_SELECT) ||
            // Compose TV often only reacts to a tap on the tile.
            tapAt(targetBox.centerX().toFloat(), targetBox.centerY().toFloat()) ||
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun focusCandidates(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val metrics = screenSize()
        return walk(root).filter { node ->
            if (!node.isVisibleToUser) return@filter false
            if (!(node.isClickable || node.isFocusable || node.isSelected)) return@filter false
            val box = Rect().also { node.getBoundsInScreen(it) }
            if (box.isEmpty) return@filter false
            // Ignore full-screen containers; keep posters / menu rows.
            box.width() in 48 until (metrics.widthPixels - 40) &&
                box.height() in 48 until (metrics.heightPixels - 40)
        }
    }

    private fun scroll(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val forward = direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN
        val horizontal = direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT
        val nodes = walk(root).filter { it.isVisibleToUser && it.isScrollable }
        val ordered = nodes.sortedByDescending { node ->
            val box = Rect().also { node.getBoundsInScreen(it) }
            if (horizontal) box.width() else box.height()
        }
        for (node in ordered) {
            val box = Rect().also { node.getBoundsInScreen(it) }
            val looksHorizontal = box.width() >= box.height()
            if (horizontal != looksHorizontal && ordered.size > 1) continue
            if (Build.VERSION.SDK_INT >= 29) {
                val action = when (direction) {
                    View.FOCUS_RIGHT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
                    View.FOCUS_LEFT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
                    View.FOCUS_DOWN -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
                    View.FOCUS_UP -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
                    else -> null
                }
                if (action != null && node.actionList.contains(action) && node.performAction(action.id)) {
                    return true
                }
            }
            val legacy = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (node.performAction(legacy)) return true
        }
        return false
    }

    private fun swipe(direction: Int): Boolean {
        val metrics = screenSize()
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val distance = minOf(metrics.widthPixels, metrics.heightPixels) * 0.22f
        val (x1, y1, x2, y2) = when (direction) {
            View.FOCUS_RIGHT -> floatArrayOf(cx + distance, cy, cx - distance, cy)
            View.FOCUS_LEFT -> floatArrayOf(cx - distance, cy, cx + distance, cy)
            View.FOCUS_DOWN -> floatArrayOf(cx, cy + distance, cx, cy - distance)
            else -> floatArrayOf(cx, cy - distance, cx, cy + distance)
        }
        return swipeFrom(x1, y1, x2, y2)
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

    private fun swipeFrom(x1: Float, y1: Float, x2: Float, y2: Float): Boolean = try {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 140)
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
        private const val MAX_NODES = 220

        @Volatile
        private var instance: RemoteAccessibilityService? = null

        @Volatile
        private var virtualFocusBox: Rect? = null

        fun press(key: String): Boolean = try {
            instance?.press(key) ?: false
        } catch (_: Throwable) {
            false
        }

        fun connected(): Boolean = instance != null
    }
}
