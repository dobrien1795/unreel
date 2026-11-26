package com.unreel.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.unreel.app.data.AppFeature

data class DetectedSurface(
    val feature: AppFeature?,
    val isDmContext: Boolean = false,
    val isInsideReelsPlayer: Boolean = false,
    val isHomeFeedActive: Boolean = false
)

class InstagramSurfaceDetector {

    private var enteredReelsFromDm: Boolean = false
    private var dmSharedReelScrolled: Boolean = false
    private var lastWasDmScreen: Boolean = false

    fun onScrollEvent(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return
        if (isReelsPlayerVisible(rootNode) && enteredReelsFromDm) {
            dmSharedReelScrolled = true
        }
    }

    fun detect(rootNode: AccessibilityNodeInfo): DetectedSurface {
        // Strict isolation: only inspect Instagram package nodes; never treat other apps as Instagram surfaces
        val pkg = rootNode.packageName?.toString()
        if (pkg != null && pkg != "com.instagram.android") {
            enteredReelsFromDm = false
            dmSharedReelScrolled = false
            lastWasDmScreen = false
            return DetectedSurface(feature = null)
        }

        val inDmThread = isDirectMessageScreen(rootNode)

        if (inDmThread) {
            lastWasDmScreen = true
            enteredReelsFromDm = false
            dmSharedReelScrolled = false
            return DetectedSurface(feature = null, isDmContext = true)
        }

        if (isReelsPlayerVisible(rootNode)) {
            if (lastWasDmScreen && !enteredReelsFromDm) {
                enteredReelsFromDm = true
                dmSharedReelScrolled = false
            }
            lastWasDmScreen = false

            // Strict DM Reels rule: single shared Reel is allowed until user scrolls to another Reel
            val isSafeSingleDmClip = enteredReelsFromDm && !dmSharedReelScrolled
            return DetectedSurface(
                feature = if (isSafeSingleDmClip) null else AppFeature.REELS,
                isDmContext = isSafeSingleDmClip,
                isInsideReelsPlayer = true
            )
        }

        enteredReelsFromDm = false
        dmSharedReelScrolled = false
        lastWasDmScreen = false

        if (isExploreSurface(rootNode)) {
            return DetectedSurface(feature = AppFeature.EXPLORE)
        }

        if (isHomeOrStoriesSurface(rootNode)) {
            return DetectedSurface(feature = AppFeature.HOME_AND_STORIES, isHomeFeedActive = true)
        }

        return DetectedSurface(feature = null)
    }

    private fun isDirectMessageScreen(root: AccessibilityNodeInfo): Boolean {
        val dmViewIds = listOf(
            "com.instagram.android:id/row_thread_composer",
            "com.instagram.android:id/direct_inbox_list",
            "com.instagram.android:id/message_list"
        )
        if (dmViewIds.any { hasNodeWithViewId(root, it) }) {
            return true
        }
        val dmTextHints = listOf("Message...", "Send message")
        return dmTextHints.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
    }

    private fun isReelsPlayerVisible(root: AccessibilityNodeInfo): Boolean {
        val reelsIds = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_root"
        )
        if (reelsIds.any { hasNodeWithViewId(root, it) }) {
            return true
        }
        val reelsTabs = root.findAccessibilityNodeInfosByText("Reels")
        return reelsTabs.any { it.isSelected && it.contentDescription?.contains("Reels", ignoreCase = true) == true }
    }

    private fun isExploreSurface(root: AccessibilityNodeInfo): Boolean {
        val exploreIds = listOf(
            "com.instagram.android:id/search_edit_text",
            "com.instagram.android:id/explore_grid_container",
            "com.instagram.android:id/action_bar_search_edit_text"
        )
        if (exploreIds.any { hasNodeWithViewId(root, it) }) {
            return true
        }
        val searchTabs = root.findAccessibilityNodeInfosByText("Search and explore")
        return searchTabs.any { it.isSelected }
    }

    private fun isHomeOrStoriesSurface(root: AccessibilityNodeInfo): Boolean {
        val homeIds = listOf(
            "com.instagram.android:id/tray_list_view",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/feed_list",
            "com.instagram.android:id/recycler_view"
        )
        if (homeIds.any { hasNodeWithViewId(root, it) }) {
            return true
        }
        val homeTabs = root.findAccessibilityNodeInfosByText("Home")
        return homeTabs.any { it.isSelected }
    }

    private fun hasNodeWithViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return runCatching { root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty() }.getOrDefault(false)
    }
}
