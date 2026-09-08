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
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.TimeUnit

class RemoteAccessibilityService : AccessibilityService() {
    private val TAG = "RemoteA11y"
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
        RemoteCursor.hide()
        when (key) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK) || injectKey(KeyEvent.KEYCODE_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME) || openHome()
            "ok" -> activateFocus()
            "up" -> move(View.FOCUS_UP)
            "down" -> move(View.FOCUS_DOWN)
            "left" -> move(View.FOCUS_LEFT)
            "right" -> move(View.FOCUS_RIGHT)
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    /** Focus move like a real remote. Soft cursor is never used. */
    fun moveFocus(key: String): Boolean {
        return try {
            RemoteCursor.hide()
            val direction = when (key) {
                "up" -> View.FOCUS_UP
                "down" -> View.FOCUS_DOWN
                "left" -> View.FOCUS_LEFT
                "right" -> View.FOCUS_RIGHT
                else -> return false
            }
            move(direction)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isKinopoisk(): Boolean {
        val pkg = try {
            activeRoot()?.packageName?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }
        return pkg.contains("kinopoisk", ignoreCase = true)
    }

    fun snapCursor(): Boolean {
        // Soft cursor mode is retired — keep the API for older callers.
        RemoteCursor.hide()
        return false
    }

    private fun activateFocus(): Boolean {
        if (injectKey(KeyEvent.KEYCODE_DPAD_CENTER)) return true
        if (clickFocused() || clickVirtual()) return true
        if (isKinopoisk() && activateRailSelection()) return true
        return false
    }

    private fun activateRailSelection(): Boolean {
        val root = activeRoot() ?: return false
        val focused = focusedNode(root) ?: return false
        val box = Rect().also { focused.getBoundsInScreen(it) }
        if (!isNavRail(box)) return false
        return activate(focused)
    }

    private fun move(direction: Int): Boolean {
        if (injectKey(directionKey(direction))) return true
        // Kinopoisk rail is real Views — walk it like DPAD when key injection is blocked.
        // No app-specific jumps (LEFT must not teleport to Music).
        if (isKinopoisk() && moveKinopoiskRail(direction)) return true
        if (stepFocus(direction)) return true
        if (step(direction)) return true
        if (scroll(direction)) return true
        return swipe(direction)
    }

    /**
     * Kinopoisk left rail is real Views (`musicButton`, …). Physical remote uses DPAD;
     * when injection is denied, focus those rail nodes in order.
     */
    private fun moveKinopoiskRail(direction: Int): Boolean {
        val root = activeRoot() ?: return false
        val rail = railButtons(root)
        if (rail.isEmpty()) return false
        val focused = focusedNode(root)
        val focusedBox = Rect().also { focused?.getBoundsInScreen(it) }
        val onRail = focused != null && !focusedBox.isEmpty && isNavRail(focusedBox)

        when (direction) {
            View.FOCUS_LEFT -> {
                if (onRail) return focusNode(focused!!)
                val originY = when {
                    focused != null && !focusedBox.isEmpty -> focusedBox.centerY()
                    virtualFocusBox != null -> virtualFocusBox!!.centerY()
                    else -> screenSize().heightPixels / 2
                }
                val target = rail.firstOrNull { it.isSelected || it.isFocused || it.isAccessibilityFocused }
                    ?: rail.minByOrNull { node ->
                        val box = Rect().also { node.getBoundsInScreen(it) }
                        kotlin.math.abs(box.centerY() - originY)
                    } ?: return false
                val ok = focusNode(target)
                Log.i(TAG, "rail LEFT -> ${labelOf(target)} ok=$ok")
                return ok
            }
            View.FOCUS_UP, View.FOCUS_DOWN -> {
                val origin = when {
                    onRail -> focusedBox
                    virtualFocusBox != null && isNavRail(virtualFocusBox!!) -> virtualFocusBox!!
                    else -> return false
                }
                val current = if (onRail) focused else {
                    rail.firstOrNull { node ->
                        val box = Rect().also { node.getBoundsInScreen(it) }
                        box.centerY() == origin.centerY() || Rect.intersects(box, origin)
                    }
                }
                val target = rail
                    .asSequence()
                    .filter { it !== current }
                    .map { it to Rect().also { box -> it.getBoundsInScreen(box) } }
                    .filter { (_, box) -> inDirection(origin, box, direction) }
                    .minByOrNull { (_, box) -> score(origin, box, direction) }
                    ?.first
                    ?: return false
                val ok = focusNode(target)
                Log.i(TAG, "rail ${if (direction == View.FOCUS_DOWN) "DOWN" else "UP"} -> ${labelOf(target)} ok=$ok")
                return ok
            }
            View.FOCUS_RIGHT -> {
                if (!onRail && (virtualFocusBox == null || !isNavRail(virtualFocusBox!!))) return false
                virtualFocusBox = null
                return step(direction) || stepFocus(direction)
            }
            else -> return false
        }
    }

    private fun railButtons(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val metrics = screenSize()
        val railRight = metrics.widthPixels * 0.20f
        val seen = LinkedHashMap<String, AccessibilityNodeInfo>()
        for (node in walk(root)) {
            if (!node.isVisibleToUser) continue
            if (!(node.isClickable || node.isFocusable)) continue
            val box = Rect().also { node.getBoundsInScreen(it) }
            if (box.isEmpty) continue
            if (box.centerX() >= railRight) continue
            if (box.width() >= metrics.widthPixels * 0.40f) continue
            if (box.height() !in 48..110) continue
            val id = node.viewIdResourceName.orEmpty()
            val label = labelOf(node)
            val named = id.contains("Button", ignoreCase = true) || label.isNotBlank()
            val leftIcon = id.isBlank() && label.isBlank()
            if (!named && !leftIcon) continue
            val key = id.ifBlank { "${box.centerY()}:${box.height()}" }
            val prev = seen[key]
            if (prev == null || (labelOf(prev).isBlank() && label.isNotBlank())) {
                seen[key] = node
            }
        }
        return seen.values.sortedBy { node ->
            Rect().also { node.getBoundsInScreen(it) }.centerY()
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        val self = node.text?.toString()?.trim().orEmpty()
            .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
        if (self.isNotBlank()) return self
        val id = node.viewIdResourceName.orEmpty()
        if (id.isNotBlank()) return id.substringAfterLast('/')
        val childCount = node.childCount.coerceAtMost(6)
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val text = child.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun isNavRail(box: Rect): Boolean {
        val metrics = screenSize()
        return box.centerX() < metrics.widthPixels * 0.22f &&
            box.width() < metrics.widthPixels * 0.40f
    }

    private fun focusNode(node: AccessibilityNodeInfo): Boolean {
        val box = Rect().also { node.getBoundsInScreen(it) }
        virtualFocusBox = Rect(box)
        if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true
        if (node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) return true
        if (node.performAction(AccessibilityNodeInfo.ACTION_SELECT)) return true
        return tapAt(box.centerX().toFloat(), box.centerY().toFloat())
    }

    private fun tapCursor(): Boolean {
        if (!RemoteCursor.visible) return false
        return tapAt(RemoteCursor.pointX, RemoteCursor.pointY)
    }

    private fun navRailRight(): Float = screenSize().widthPixels * 0.18f

    private fun pressAt(x: Float, y: Float): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 160)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    } catch (_: Throwable) {
        false
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
        if (injectKeyReflect(keyCode)) return true
        return injectKeyShell(keyCode)
    }

    private fun injectKeyReflect(keyCode: Int): Boolean {
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

    /**
     * MiTV denies INJECT_EVENTS to the app, and `/system/bin/input` usually exits 1
     * under the app uid. Cache the failure so arrows stay snappy.
     */
    private fun injectKeyShell(keyCode: Int): Boolean {
        if (shellKeysOk == false) return false
        return try {
            val process = ProcessBuilder("/system/bin/input", "keyevent", keyCode.toString())
                .redirectErrorStream(true)
                .start()
            val err = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                shellKeysOk = false
                Log.w(TAG, "input keyevent timed out")
                return false
            }
            val code = process.exitValue()
            if (code != 0) {
                Log.w(TAG, "input keyevent $keyCode exit=$code err=$err")
                shellKeysOk = false
                return false
            }
            shellKeysOk = true
            true
        } catch (error: Throwable) {
            Log.w(TAG, "input keyevent failed", error)
            shellKeysOk = false
            false
        }
    }

    /**
     * Content-area swipe from the software cursor (or screen center).
     * Used when Kinopoisk Music exposes no focusable tiles.
     */
    fun scrollByPad(key: String): Boolean {
        val direction = when (key) {
            "up" -> View.FOCUS_UP
            "down" -> View.FOCUS_DOWN
            "left" -> View.FOCUS_LEFT
            "right" -> View.FOCUS_RIGHT
            else -> return false
        }
        if (scroll(direction)) return true
        return swipeFromCursor(direction)
    }

    private fun swipeFromCursor(direction: Int): Boolean {
        val metrics = screenSize()
        val cx = if (RemoteCursor.visible) {
            RemoteCursor.pointX.coerceAtLeast(metrics.widthPixels * 0.28f)
        } else {
            metrics.widthPixels * 0.55f
        }
        val cy = if (RemoteCursor.visible) RemoteCursor.pointY else metrics.heightPixels * 0.5f
        // Longer vertical flick so Kinopoisk Music rows actually advance.
        val distance = minOf(metrics.widthPixels, metrics.heightPixels) *
            if (isKinopoisk()) 0.38f else 0.28f
        val (x1, y1, x2, y2) = when (direction) {
            View.FOCUS_RIGHT -> floatArrayOf(cx + distance * 0.5f, cy, cx - distance * 0.5f, cy)
            View.FOCUS_LEFT -> floatArrayOf(cx - distance * 0.5f, cy, cx + distance * 0.5f, cy)
            View.FOCUS_DOWN -> floatArrayOf(cx, cy + distance * 0.25f, cx, cy - distance * 0.7f)
            else -> floatArrayOf(cx, cy - distance * 0.25f, cx, cy + distance * 0.7f)
        }
        return swipeFrom(x1, y1, x2, y2, if (isKinopoisk()) 220 else 140)
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
        if (node.performAction(AccessibilityNodeInfo.ACTION_SELECT)) {
            // Some TV rows select first; a second click opens.
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        var parent = node.parent
        var hops = 0
        while (parent != null && hops < 10) {
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

    private fun step(direction: Int, allowTap: Boolean = true): Boolean {
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
            .filter { (_, box) -> !isNavRail(box) || originBox.centerX() < navRailRight() }
            .filter { (_, box) -> inDirection(originBox, box, direction) }
            .minByOrNull { (_, box) -> score(originBox, box, direction) }
            ?.first
            ?: return false
        val targetBox = Rect().also { target.getBoundsInScreen(it) }
        virtualFocusBox = Rect(targetBox)
        if (target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        ) {
            return true
        }
        if (!allowTap) return false
        // Compose TV often only reacts to a tap on the tile.
        return tapAt(targetBox.centerX().toFloat(), targetBox.centerY().toFloat()) ||
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun focusCandidates(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val metrics = screenSize()
        return walk(root).filter { node ->
            if (!node.isVisibleToUser) return@filter false
            if (node.isEditable) return@filter false
            val className = node.className?.toString().orEmpty()
            if (className.contains("EditText", ignoreCase = true)) return@filter false
            if (!(node.isClickable || node.isFocusable || node.isSelected)) return@filter false
            val box = Rect().also { node.getBoundsInScreen(it) }
            if (box.isEmpty) return@filter false
            // Ignore full-screen containers and tiny chrome; keep posters / menu rows.
            box.width() in 64 until (metrics.widthPixels - 40) &&
                box.height() in 48 until (metrics.heightPixels - 80)
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

    private fun swipeFrom(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 140): Boolean = try {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
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
        private const val MAX_NODES = 400

        @Volatile
        private var instance: RemoteAccessibilityService? = null

        @Volatile
        private var virtualFocusBox: Rect? = null

        /** null = unknown, true/false = last shell keyevent result (cached). */
        @Volatile
        private var shellKeysOk: Boolean? = null

        fun press(key: String): Boolean = try {
            instance?.press(key) ?: false
        } catch (_: Throwable) {
            false
        }

        fun moveFocus(key: String): Boolean = try {
            instance?.moveFocus(key) ?: false
        } catch (_: Throwable) {
            false
        }

        /** Soft cursor is retired; kept so older callers compile. */
        fun needsPointer(): Boolean = false

        fun snapCursor(): Boolean = try {
            instance?.snapCursor() ?: false
        } catch (_: Throwable) {
            false
        }

        fun scrollByPad(key: String): Boolean = try {
            instance?.scrollByPad(key) ?: false
        } catch (_: Throwable) {
            false
        }

        fun connected(): Boolean = instance != null

        fun debugRail(): String = try {
            instance?.debugRailState() ?: "a11y offline"
        } catch (error: Throwable) {
            "rail error: ${error.message}"
        }
    }

    fun debugRailState(): String {
        val root = activeRoot() ?: return "no root"
        val focused = focusedNode(root)
        val rail = railButtons(root)
        val focusId = focused?.let { labelOf(it).ifBlank { it.viewIdResourceName ?: "node" } } ?: "none"
        val railIds = rail.joinToString { node ->
            val box = Rect().also { node.getBoundsInScreen(it) }
            "${labelOf(node).ifBlank { "row" }}@${box.centerY()}f=${node.isFocused}/${node.isAccessibilityFocused}"
        }
        return "pkg=${root.packageName} focus=$focusId virtual=$virtualFocusBox rail=[$railIds]"
    }
}
