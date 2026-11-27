package com.unreel.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.unreel.app.data.WarningLevel

class OverlayAlertManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bannerView: View? = null
    private var lastShownSignature: String? = null

    fun showCountdownWarning(remainingSeconds: Long, level: WarningLevel) {
        if (!Settings.canDrawOverlays(context)) return
        if (level == WarningLevel.NONE) {
            dismissBanner()
            return
        }

        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val formatted = String.format("%d:%02d", mins, secs)
        val label = if (level == WarningLevel.THIRTY_SECONDS) {
            "Unreel: $formatted left — wrapping up soon"
        } else {
            "Unreel: $formatted remaining"
        }

        if (bannerView != null && (bannerView as? TextView) != null) {
            (bannerView as TextView).text = label
            return
        }

        dismissBanner()

        val pill = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(36, 18, 36, 18)
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(
                    if (level == WarningLevel.THIRTY_SECONDS) Color.parseColor("#D32F2F")
                    else Color.parseColor("#E65100")
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 110
        }

        runCatching {
            windowManager.addView(pill, params)
            bannerView = pill
            lastShownSignature = "WARN:$label"
        }
    }

    fun showBlockedToastBanner(
        reason: String,
        sectionName: String = "Reels",
        timeExpired: Boolean = false
    ) {
        if (!Settings.canDrawOverlays(context)) return

        val heading = if (timeExpired) {
            "Time's up — $sectionName closed"
        } else {
            "$sectionName are blocked"
        }

        val signature = "BLOCK:$heading|$reason"
        if (bannerView != null && lastShownSignature == signature) {
            return
        }

        dismissBanner()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 28, 44, 28)
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.parseColor("#181C24"))
                setStroke(
                    3,
                    if (timeExpired) Color.parseColor("#F28B82") else Color.parseColor("#8AB4F8")
                )
            }
        }

        val title = TextView(context).apply {
            text = heading
            setTextColor(if (timeExpired) Color.parseColor("#F28B82") else Color.parseColor("#8AB4F8"))
            textSize = 16f
        }
        val body = TextView(context).apply {
            text = if (timeExpired) "Your time has run out. $reason" else reason
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        container.addView(title)
        container.addView(body)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 150
        }

        runCatching {
            windowManager.addView(container, params)
            bannerView = container
            lastShownSignature = signature
            container.postDelayed({ dismissBanner() }, 2800L)
        }
    }

    fun dismissBanner() {
        bannerView?.let { view ->
            runCatching { windowManager.removeView(view) }
            bannerView = null
            lastShownSignature = null
        }
    }
}
