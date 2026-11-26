package com.changsheng.carlauncher

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class RunningOrangeBorderDrawable : Drawable() {
    private var angle = 0f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EB0C121E") }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val matrix = Matrix()
    private var animator: ValueAnimator? = null

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun draw(canvas: Canvas) {
        val r = RectF(bounds)
        val inset = 2.5f
        r.inset(inset, inset)
        val radius = r.height() / 2f
        canvas.drawRoundRect(r, radius, radius, bgPaint)

        val sweep = SweepGradient(
            r.centerX(), r.centerY(),
            intArrayOf(
                Color.argb(30, 249, 115, 22),
                Color.argb(30, 249, 115, 22),
                Color.parseColor("#F97316"),
                Color.parseColor("#FBBF24"),
                Color.argb(30, 249, 115, 22)
            ),
            floatArrayOf(0f, 0.58f, 0.78f, 0.92f, 1f)
        )
        matrix.setRotate(angle, r.centerX(), r.centerY())
        sweep.setLocalMatrix(matrix)
        borderPaint.shader = sweep
        canvas.drawRoundRect(r, radius, radius, borderPaint)
    }

    override fun setAlpha(alpha: Int) { bgPaint.alpha = alpha; borderPaint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { borderPaint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
