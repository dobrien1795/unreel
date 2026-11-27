package com.unreel.app.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.unreel.app.data.AppFeature

data class DetectedSurface(
    val feature: AppFeature?,
    val isDmContext: Boolean = false,
    val isInsideReelsPlayer: Boolean = false,
    val isHomeFeedActive: Boolean = false
)

/**
 * Classifies the active Instagram screen so rules apply strictly to the intended surface.
 * Priority order prevents DM threads, DM inbox, Profile, and Explore from being misclassified as Reels.
 */
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
        val pkg = rootNode.packageName?.toString()
        if (pkg != null && pkg != "com.instagram.android") {
            resetDmReelState()
            return DetectedSurface(feature = null)
        }

        // 1. Direct Messages first: DM inbox and chat threads must remain accessible
        // unless the user explicitly blocks Home/Stories (or Block All is enabled).
        if (isDirectMessageScreen(rootNode)) {
            lastWasDmScreen = true
            enteredReelsFromDm = false
            dmSharedReelScrolled = false
            return DetectedSurface(feature = null, isDmContext = true)
        }

        // 2. Profile screens (own profile or other user profiles) are never Reels or Explore.
        if (isProfileScreen(rootNode)) {
            lastWasDmScreen = false
            enteredReelsFromDm = false
            dmSharedReelScrolled = false
            return DetectedSurface(feature = null)
        }

        // 3. Explore screen: only match when Explore is actually the active destination.
        if (isExploreSurface(rootNode)) {
            lastWasDmScreen = false
            enteredReelsFromDm = false
            dmSharedReelScrolled = false
            return DetectedSurface(feature = AppFeature.EXPLORE)
        }

        // 4. Reels viewer: only match full-screen/active Reels clips containers or selected Reels tab.
        if (isReelsPlayerVisible(rootNode)) {
            if (lastWasDmScreen && !enteredReelsFromDm) {
                enteredReelsFromDm = true
                dmSharedReelScrolled = false
            }
            lastWasDmScreen = false

            val isSafeSingleDmClip = enteredReelsFromDm && !dmSharedReelScrolled
            return DetectedSurface(
                feature = if (isSafeSingleDmClip) null else AppFeature.REELS,
                isDmContext = isSafeSingleDmClip,
                isInsideReelsPlayer = true
            )
        }

        // 5. Home feed & Stories viewer.
        enteredReelsFromDm = false
        dmSharedReelScrolled = false
        lastWasDmScreen = false

        if (isHomeOrStoriesSurface(rootNode)) {
            return DetectedSurface(feature = AppFeature.HOME_AND_STORIES, isHomeFeedActive = true)
        }

        return DetectedSurface(feature = null)
    }

    private fun resetDmReelState() {
        enteredReelsFromDm = false
        dmSharedReelScrolled = false
        lastWasDmScreen = false
    }

    private fun isDirectMessageScreen(root: AccessibilityNodeInfo): Boolean {
        val dmViewIds = listOf(
            "com.instagram.android:id/row_thread_composer",
            "com.instagram.android:id/direct_inbox_list",
            "com.instagram.android:id/direct_thread_message_list",
            "com.instagram.android:id/direct_composer_edit_text",
            "com.instagram.android:id/direct_inbox_recycler_view",
            "com.instagram.android:id/message_list"
        )
        if (dmViewIds.any { hasVisibleNodeWithViewId(root, it) }) {
            return true
        }
        val dmTextHints = listOf("Message...", "Send message", "Write a message")
        return dmTextHints.any { text ->
            root.findAccessibilityNodeInfosByText(text).any { it.isVisibleToUser }
        }
    }

    private fun isProfileScreen(root: AccessibilityNodeInfo): Boolean {
        val profileIds = listOf(
            "com.instagram.android:id/profile_header_container",
            "com.instagram.android:id/row_profile_header",
            "com.instagram.android:id/profile_tab_layout",
            "com.instagram.android:id/profile_header_full_name"
        )
        return profileIds.any { hasVisibleNodeWithViewId(root, it) }
    }

    private fun isReelsPlayerVisible(root: AccessibilityNodeInfo): Boolean {
        // If Explore search or DM is visible, do not treat background/cached nodes as Reels.
        if (isExploreSurface(root) || isDirectMessageScreen(root)) {
            return false
        }

        val reelsIds = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_root"
        )
        if (reelsIds.any { hasLargeVisibleNodeWithViewId(root, it) }) {
            return true
        }

        val reelsTabs = root.findAccessibilityNodeInfosByText("Reels")
        return reelsTabs.any { node ->
            node.isVisibleToUser &&
                node.isSelected &&
                (node.contentDescription?.contains("Reels", ignoreCase = true) == true ||
                    node.text?.toString()?.equals("Reels", ignoreCase = true) == true)
        }
    }

    private fun isExploreSurface(root: AccessibilityNodeInfo): Boolean {
        val exploreIds = listOf(
            "com.instagram.android:id/explore_grid_container",
            "com.instagram.android:id/action_bar_search_edit_text"
        )
        if (exploreIds.any { hasVisibleNodeWithViewId(root, it) }) {
            return true
        }
        val searchTabs = root.findAccessibilityNodeInfosByText("Search and explore")
        return searchTabs.any { it.isVisibleToUser && it.isSelected }
    }

    private fun isHomeOrStoriesSurface(root: AccessibilityNodeInfo): Boolean {
        val homeIds = listOf(
            "com.instagram.android:id/tray_list_view",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/feed_list"
        )
        if (homeIds.any { hasVisibleNodeWithViewId(root, it) }) {
            return true
        }
        val homeTabs = root.findAccessibilityNodeInfosByText("Home")
        return homeTabs.any { it.isVisibleToUser && it.isSelected }
    }

    private fun hasVisibleNodeWithViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return runCatching {
            root.findAccessibilityNodeInfosByViewId(viewId).any { it.isVisibleToUser }
        }.getOrDefault(false)
    }

    private fun hasLargeVisibleNodeWithViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return runCatching {
            val rect = Rect()
            root.findAccessibilityNodeInfosByViewId(viewId).any { node ->
                if (!node.isVisibleToUser) return@any false
                node.getBoundsInScreen(rect)
                rect.width() > 200 && rect.height() > 250
            }
        }.getOrDefault(false)
    }
}
