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

        if (bannerView != null) {
            (bannerView as? TextView)?.text = label
            return
        }

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
        }
    }

    fun showBlockedToastBanner(reason: String) {
        if (!Settings.canDrawOverlays(context)) return
        dismissBanner()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 26, 44, 26)
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.parseColor("#1E1E24"))
                setStroke(2, Color.parseColor("#FF5252"))
            }
        }

        val title = TextView(context).apply {
            text = "Unreel Blocked"
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 14f
        }
        val body = TextView(context).apply {
            text = reason
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
            gravity = Gravity.CENTER
        }

        runCatching {
            windowManager.addView(container, params)
            bannerView = container
            container.postDelayed({ dismissBanner() }, 2400L)
        }
    }

    fun dismissBanner() {
        bannerView?.let { view ->
            runCatching { windowManager.removeView(view) }
            bannerView = null
        }
    }
}
