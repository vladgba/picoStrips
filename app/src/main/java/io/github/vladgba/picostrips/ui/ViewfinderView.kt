package io.github.vladgba.picostrips.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Dimmed surroundings + corner brackets (decorative: the whole frame is decoded).
 * [hit] briefly outlines the found code on the preview and tints the brackets — informational only.
 */
class ViewfinderView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val dp = resources.displayMetrics.density
    private val accent = ctx.getColor(io.github.vladgba.picostrips.R.color.accent)
    private val scrim = Paint().apply { color = 0x66000000 }
    private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * dp; strokeCap = Paint.Cap.ROUND
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3 * dp; strokeJoin = Paint.Join.ROUND; color = accent
    }

    private val frame = RectF()
    private val brackets = Path()
    private val quad = Path()
    private var hasQuad = false
    private var fade = 0f // 1 = just found, 0 = idle
    private val tween = Tween(this, 600) { fade = 1f - it; invalidate() }

    /** @param corners TL,TR,BR,BL in upright-image pixels (null = unknown, e.g. image file). */
    fun hit(corners: FloatArray?, imgW: Int, imgH: Int) {
        hasQuad = corners != null && imgW > 0 && imgH > 0 && width > 0
        if (hasQuad) {
            // The preview shows the upright image center-cropped to this view.
            val s = max(width.toFloat() / imgW, height.toFloat() / imgH)
            val dx = (width - imgW * s) / 2; val dy = (height - imgH * s) / 2
            val c = corners!!
            quad.reset()
            quad.moveTo(c[0] * s + dx, c[1] * s + dy)
            for (i in 1 until 4) quad.lineTo(c[i * 2] * s + dx, c[i * 2 + 1] * s + dy)
            quad.close()
        }
        tween.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val side = min(w, h) * 0.72f
        val left = (w - side) / 2; val top = (h - side) / 2.4f
        frame.set(left, top, left + side, top + side)
        val l = side * 0.12f
        brackets.reset()
        brackets.moveTo(frame.left, frame.top + l); brackets.lineTo(frame.left, frame.top); brackets.lineTo(frame.left + l, frame.top)
        brackets.moveTo(frame.right - l, frame.top); brackets.lineTo(frame.right, frame.top); brackets.lineTo(frame.right, frame.top + l)
        brackets.moveTo(frame.right, frame.bottom - l); brackets.lineTo(frame.right, frame.bottom); brackets.lineTo(frame.right - l, frame.bottom)
        brackets.moveTo(frame.left + l, frame.bottom); brackets.lineTo(frame.left, frame.bottom); brackets.lineTo(frame.left, frame.bottom - l)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, frame.top, scrim)
        c.drawRect(0f, frame.bottom, w, h, scrim)
        c.drawRect(0f, frame.top, frame.left, frame.bottom, scrim)
        c.drawRect(frame.right, frame.top, w, frame.bottom, scrim)
        bracket.color = if (fade > 0f) accent else 0xFFFFFFFF.toInt()
        c.drawPath(brackets, bracket)
        if (hasQuad && fade > 0f) {
            outline.alpha = (fade * 255).toInt()
            c.drawPath(quad, outline)
        }
    }

    override fun onDetachedFromWindow() {
        tween.cancel()
        fade = 0f
        super.onDetachedFromWindow()
    }
}
