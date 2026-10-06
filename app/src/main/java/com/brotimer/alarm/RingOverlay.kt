package com.brotimer.alarm

import android.app.Service
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Stay on screen while ringing": a card with Snooze and Stop that floats over whatever app is
 * open, for as long as the alarm rings.
 *
 * **Why it exists (Omar, 2026-10-06):** while the phone is unlocked and in use, Android shows a
 * ringing alarm only as a heads-up banner, and HyperOS slides that banner away after a few
 * seconds — leaving him to open the notification shade and expand the notification to reach Stop.
 * This card does not go anywhere until he presses something.
 *
 * It is a system overlay window, so it needs "Display over other apps" (`SYSTEM_ALERT_WINDOW`).
 * Overlays sit *below* the lock screen, which is fine: when the phone is locked the full-screen
 * [AlarmActivity] shows instead. Built from plain Views, not Compose — a Compose view in a
 * service-owned window would need its own lifecycle and saved-state owners for no gain here.
 */
internal class RingOverlay(
    private val service: Service,
    private val onSnooze: () -> Unit,
    private val onStop: () -> Unit,
    private val onOpen: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private var root: View? = null
    private var title: TextView? = null
    private var status: TextView? = null
    private var snooze: TextView? = null

    val isShowing: Boolean get() = root != null

    fun canShow(): Boolean = Settings.canDrawOverlays(service)

    fun show(label: String, statusText: String, snoozeMinutes: Int) {
        if (root != null) {
            update(label, statusText, snoozeMinutes)
            return
        }
        if (!canShow()) return
        val view = build()
        update(label, statusText, snoozeMinutes)
        try {
            wm.addView(view, layoutParams())
            root = view
            // Slide up from below the screen edge: it should be noticed, not just appear.
            view.translationY = dp(160).toFloat()
            view.animate().translationY(0f).setDuration(260).start()
        } catch (e: Exception) {
            Log.w(Scheduler.TAG, "could not show the stay-on-screen card", e)
            clearRefs()
        }
    }

    fun update(label: String, statusText: String, snoozeMinutes: Int) {
        title?.text = label
        status?.text = statusText
        snooze?.text = "Snooze ${snoozeMinutes}m"
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        clearRefs()
    }

    private fun clearRefs() {
        root = null
        title = null
        status = null
        snooze = null
    }

    // -- view ------------------------------------------------------------------------------------

    private fun build(): View {
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            // Fully opaque: at 95% the home-screen icons underneath showed through the buttons.
            background = rounded(0xFF101A33.toInt(), 26, stroke = 0x40FFFFFF)
            elevation = dp(10).toFloat()
        }

        // Tapping the text opens the full alarm screen.
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener { onOpen() }
        }
        title = TextView(service).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        status = TextView(service).apply {
            setTextColor(0xFF9FB3C8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(3), 0, 0)
        }
        header.addView(title)
        header.addView(status)
        card.addView(header)

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        snooze = button("Snooze", 0x2EFFFFFF, 0xFFFFFFFF.toInt()) { onSnooze() }
        val stop = button("■  Stop", 0xFFF4BF48.toInt(), 0xFF2A1D00.toInt()) { onStop() }
        buttons.addView(snooze, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        buttons.addView(stop, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        card.addView(buttons)

        return FrameLayout(service).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(
                card,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun button(text: String, fill: Int, textColor: Int, onClick: () -> Unit) =
        TextView(service).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(fill, 20)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable: Back and the keyboard keep working in the app underneath.
        // Not touch-modal: touches outside the card go to that app too.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        y = bottomInset() + dp(20)
        title = "BroTimer alarm"
    }

    /** Keep the card above the navigation bar / gesture area. */
    private fun bottomInset(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                wm.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                    .bottom
            }.getOrDefault(dp(48))
        } else {
            dp(48)
        }

    private fun dp(v: Int) = (v * density).toInt()
}
