package com.unreel.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
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
    private var lastBlockActionMs: Long = 0L

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
            activePackage = null
            activeFeature = null
            overlayManager.dismissBanner()
            return
        }

        activePackage = pkg
        val root = rootInActiveWindow ?: return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            detector.onScrollEvent(root)
        }

        val surface = detector.detect(root)
        activeFeature = surface.feature

        if (surface.feature == null) {
            overlayManager.dismissBanner()
            return
        }

        serviceScope.launch {
            enforceFeature(pkg, surface.feature, elapsedSeconds = 0L)
        }
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val pkg = activePackage
                val feature = activeFeature
                if (pkg != null && feature != null) {
                    enforceFeature(pkg, feature, elapsedSeconds = 1L)
                }
            }
        }
    }

    private suspend fun enforceFeature(
        packageName: String,
        feature: AppFeature,
        elapsedSeconds: Long
    ) {
        when (val result = usageTracker.tickAndEvaluate(packageName, feature, elapsedSeconds)) {
            is EvaluationResult.Allowed -> {
                overlayManager.dismissBanner()
            }
            is EvaluationResult.TimedActive -> {
                overlayManager.showCountdownWarning(result.remainingSeconds, result.warningLevel)
            }
            is EvaluationResult.Blocked -> {
                overlayManager.showBlockedToastBanner(result.reason)
                triggerBackOut()
            }
        }
    }

    private fun triggerBackOut() {
        val now = System.currentTimeMillis()
        if (now - lastBlockActionMs > 450L) {
            lastBlockActionMs = now
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        tickerJob?.cancel()
        overlayManager.dismissBanner()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        overlayManager.dismissBanner()
        super.onDestroy()
    }
}
