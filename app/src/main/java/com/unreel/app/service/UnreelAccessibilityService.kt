package com.unreel.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.unreel.app.data.AppFeature
import com.unreel.app.data.EvaluationResult
import com.unreel.app.data.PolicyRepository
import com.unreel.app.data.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UnreelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: PolicyRepository
    private lateinit var usageTracker: UsageTracker
    private lateinit var overlayManager: OverlayAlertManager
    private val detector = InstagramSurfaceDetector()

    private var activePackage: String? = null
    private var activeFeature: AppFeature? = null
    private var tickerJob: Job? = null
    private var lastRedirectActionMs: Long = 0L
    private var pendingRedirectSteps: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = PolicyRepository(applicationContext)
        usageTracker = UsageTracker(repository)
        overlayManager = OverlayAlertManager(applicationContext)
        startTickerLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (pkg != "com.instagram.android") {
            clearInstagramState()
            return
        }

        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString()
        if (rootPkg != null && rootPkg != "com.instagram.android") {
            clearInstagramState()
            return
        }

        activePackage = pkg

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            detector.onScrollEvent(root)
        }

        val surface = detector.detect(root)
        activeFeature = surface.feature

        if (surface.feature == null) {
            if (surface.isHomeFeedActive) {
                pendingRedirectSteps = 0
            }
            overlayManager.dismissBanner()
            return
        }

        serviceScope.launch {
            enforceFeature(pkg, surface.feature, root, surface.isHomeFeedActive, elapsedSeconds = 0L)
        }
    }

    private fun clearInstagramState() {
        activePackage = null
        activeFeature = null
        pendingRedirectSteps = 0
        overlayManager.dismissBanner()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val pkg = activePackage
                val feature = activeFeature
                val root = rootInActiveWindow
                if (pkg != "com.instagram.android" || feature == null || root == null) {
                    continue
                }
                val rootPkg = root.packageName?.toString()
                if (rootPkg != null && rootPkg != "com.instagram.android") {
                    clearInstagramState()
                    continue
                }
                val surface = detector.detect(root)
                if (surface.feature == null) {
                    activeFeature = null
                    overlayManager.dismissBanner()
                    continue
                }
                enforceFeature(pkg, surface.feature, root, surface.isHomeFeedActive, elapsedSeconds = 1L)
            }
        }
    }

    private suspend fun enforceFeature(
        packageName: String,
        feature: AppFeature,
        root: AccessibilityNodeInfo,
        isHomeFeedActive: Boolean,
        elapsedSeconds: Long
    ) {
        when (val result = usageTracker.tickAndEvaluate(packageName, feature, elapsedSeconds)) {
            is EvaluationResult.Allowed -> {
                pendingRedirectSteps = 0
                overlayManager.dismissBanner()
            }
            is EvaluationResult.TimedActive -> {
                pendingRedirectSteps = 0
                overlayManager.showCountdownWarning(result.sessionRemainingSeconds, result.warningLevel)
            }
            is EvaluationResult.Blocked -> {
                val reason = if (feature == AppFeature.REELS && !result.timeExpired) {
                    "Reels are blocked — returning to main feed."
                } else {
                    result.reason
                }
                overlayManager.showBlockedToastBanner(
                    reason = reason,
                    sectionName = feature.displayName,
                    timeExpired = result.timeExpired
                )
                if (feature == AppFeature.REELS) {
                    returnToMainFeedSafely(root, isHomeFeedActive)
                }
            }
        }
    }

    private fun returnToMainFeedSafely(root: AccessibilityNodeInfo, isHomeFeedActive: Boolean) {
        if (isHomeFeedActive) {
            pendingRedirectSteps = 0
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRedirectActionMs < 650L) return

        if (clickHomeTabIfPresent(root)) {
            lastRedirectActionMs = now
            pendingRedirectSteps = 0
            return
        }

        if (pendingRedirectSteps < 1) {
            lastRedirectActionMs = now
            pendingRedirectSteps += 1
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            activeFeature = null
        }
    }

    private fun clickHomeTabIfPresent(root: AccessibilityNodeInfo): Boolean {
        val candidates = root.findAccessibilityNodeInfosByText("Home")
        for (node in candidates) {
            if (node.isSelected) return true
            val clickable = findClickableAncestor(node)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth += 1
        }
        return null
    }

    override fun onInterrupt() {
        tickerJob?.cancel()
        clearInstagramState()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        clearInstagramState()
        super.onDestroy()
    }
}
