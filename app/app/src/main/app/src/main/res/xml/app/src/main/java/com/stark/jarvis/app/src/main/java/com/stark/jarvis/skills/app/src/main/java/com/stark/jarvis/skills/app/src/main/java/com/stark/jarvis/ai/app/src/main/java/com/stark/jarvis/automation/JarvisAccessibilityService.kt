package com.stark.jarvis.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object { var instance: JarvisAccessibilityService? = null }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickTextOnScreen(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        root.findAccessibilityNodeInfosByText(targetText).forEach { if (clickNodeOrParent(it)) return true }
        return searchByDesc(root, targetText)
    }

    private fun searchByDesc(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        if (node.contentDescription?.toString()?.equals(target, ignoreCase = true) == true) {
            if (clickNodeOrParent(node)) return true
        }
        for (i in 0 until node.childCount) {
            if (searchByDesc(node.getChild(i), target)) return true
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) { parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
            parent = parent.parent
        }
        return false
    }
}
