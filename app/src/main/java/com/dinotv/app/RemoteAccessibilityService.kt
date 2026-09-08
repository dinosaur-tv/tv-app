package com.dinotv.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 0
        } ?: return
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun press(key: String): Boolean = when (key) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(home)
                true
            } catch (_: Exception) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        "ok" -> clickFocused()
        "up", "down", "left", "right" -> moveFocus(key)
        else -> false
    }

    private fun clickFocused(): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: return false
        return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            focused.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun moveFocus(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val origin = Rect()
        focused?.getBoundsInScreen(origin)
        if (focused == null || origin.isEmpty) {
            origin.set(root.boundsCenter())
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        val target = nodes
            .asSequence()
            .filter { it !== focused && it.isVisibleToUser && (it.isClickable || it.isFocusable || it.isCheckable) }
            .map { node -> node to node.bounds() }
            .filter { (_, box) -> !box.isEmpty }
            .filter { (_, box) -> inDirection(origin, box, direction) }
            .minByOrNull { (_, box) -> score(origin, box, direction) }
            ?.first
            ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out += node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
        }
    }

    private fun AccessibilityNodeInfo.bounds(): Rect = Rect().also { getBoundsInScreen(it) }

    private fun AccessibilityNodeInfo.boundsCenter(): Rect {
        val box = bounds()
        val cx = box.centerX()
        val cy = box.centerY()
        return Rect(cx, cy, cx + 1, cy + 1)
    }

    private fun inDirection(from: Rect, to: Rect, direction: String): Boolean {
        val dx = to.centerX() - from.centerX()
        val dy = to.centerY() - from.centerY()
        return when (direction) {
            "up" -> dy < -8 && abs(dy) >= abs(dx) / 2
            "down" -> dy > 8 && abs(dy) >= abs(dx) / 2
            "left" -> dx < -8 && abs(dx) >= abs(dy) / 2
            "right" -> dx > 8 && abs(dx) >= abs(dy) / 2
            else -> false
        }
    }

    private fun score(from: Rect, to: Rect, direction: String): Int {
        val dx = abs(to.centerX() - from.centerX())
        val dy = abs(to.centerY() - from.centerY())
        return when (direction) {
            "up", "down" -> dy * 3 + dx
            else -> dx * 3 + dy
        }
    }

    companion object {
        @Volatile
        private var instance: RemoteAccessibilityService? = null

        fun press(key: String): Boolean = instance?.press(key) ?: false

        fun connected(): Boolean = instance != null
    }
}
