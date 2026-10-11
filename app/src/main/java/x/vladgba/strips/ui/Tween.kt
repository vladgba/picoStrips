package x.vladgba.strips.ui

import android.os.SystemClock
import android.view.View

/**
 * Minimal frame-driven tween on its own clock (View.postOnAnimation + uptime).
 * Unlike ValueAnimator / ViewPropertyAnimator it ignores the system "Animator duration scale",
 * so it still plays when animations are disabled in Developer options, battery saver or accessibility.
 */
class Tween(private val view: View, var duration: Long, private val onUpdate: (Float) -> Unit) {
    private var startTime = 0L
    private var onEnd: (() -> Unit)? = null
    var isRunning = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val t = ((SystemClock.uptimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
            onUpdate(t)
            if (t >= 1f) {
                isRunning = false
                val end = onEnd
                onEnd = null
                end?.invoke()
            } else {
                view.postOnAnimation(this)
            }
        }
    }

    /** Restarts from 0; a previous run is cancelled without calling its end action. */
    fun start(onEnd: (() -> Unit)? = null) {
        cancel()
        this.onEnd = onEnd
        startTime = SystemClock.uptimeMillis()
        isRunning = true
        onUpdate(0f)
        view.postOnAnimation(tick)
    }

    fun cancel() {
        isRunning = false
        onEnd = null
        view.removeCallbacks(tick)
    }
}
