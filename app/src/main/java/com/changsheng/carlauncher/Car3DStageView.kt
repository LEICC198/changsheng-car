package com.changsheng.carlauncher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ultra-lightweight native 3D car & road visualizer optimized for SC9853i (Android 8.1):
 * - 100% offline, ~5MB RAM (zero WebView/GPU bloat)
 * - Auto-pauses rendering at 0% CPU/GPU when stationary & idle
 * - Features: feathered soft headlights, bright red brake glow, "长生车机" license plate,
 *   dark asphalt road, scrolling lane dashes + roadside trees & city silhouettes synced to km/h.
 */
class Car3DStageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var speedKmh: Float = 0f
        set(value) {
            field = value
            if (abs(value) > 0.2f) scheduleNextFrame()
            else invalidate()
        }
    var handbrakeOn: Boolean = true
        set(value) { field = value; invalidate() }
    var headlightOn: Boolean = true
        set(value) { field = value; invalidate() }
    var reverseOn: Boolean = false
        set(value) { field = value; if (value) scheduleNextFrame() else invalidate() }

    private var yawOffset = 0f
    private var lastTouchX = 0f
    private var roadPhase = 0f
    private var lastFrameNs = 0L
    private var animRunning = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val frameRunnable = Runnable {
        animRunning = false
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.033f else ((now - lastFrameNs) / 1e9f).coerceAtMost(0.05f)
        lastFrameNs = now

        val activeSpd = if (handbrakeOn) 0f else if (reverseOn) -12f else speedKmh
        if (abs(activeSpd) > 0.2f) {
            roadPhase = (roadPhase + (activeSpd / 3.6f) * dt * 14f) % 100f
            if (roadPhase < 0f) roadPhase += 100f
            invalidate()
            scheduleNextFrame()
        } else {
            lastFrameNs = 0L
        }
    }

    private fun scheduleNextFrame() {
        if (!animRunning && isAttachedToWindow) {
            animRunning = true
            postDelayed(frameRunnable, 33L) // Smooth 30fps cap for SC9853i efficiency
        }
    }

    fun pauseAnimation() {
        removeCallbacks(frameRunnable)
        animRunning = false
        lastFrameNs = 0L
    }

    fun resumeAnimation() {
        if (!handbrakeOn && (speedKmh > 0.2f || reverseOn)) scheduleNextFrame()
    }

    fun resetCamera() {
        yawOffset = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                lastTouchX = event.x
                yawOffset = (yawOffset + dx * 0.22f).coerceIn(-28f, 28f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f + yawOffset * 0.6f
        val horizonY = h * 0.14f

        // 1. Dark Matte Ground
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#05080F"), Color.parseColor("#0B1120"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2. Perspective Dark Asphalt Road
        val roadPath = Path().apply {
            moveTo(cx - w * 0.20f, horizonY)
            lineTo(cx + w * 0.20f, horizonY)
            lineTo(cx + w * 0.44f, h)
            lineTo(cx - w * 0.44f, h)
            close()
        }
        paint.color = Color.parseColor("#0E1525")
        canvas.drawPath(roadPath, paint)

        // Road Outer Lines
        paint.color = Color.parseColor("#6694A3B8")
        paint.strokeWidth = 3f
        canvas.drawLine(cx - w * 0.19f, horizonY, cx - w * 0.42f, h, paint)
        canvas.drawLine(cx + w * 0.19f, horizonY, cx + w * 0.42f, h, paint)

        // Animated Center Dashes & Roadside Trees/Buildings (Never blocking car!)
        paint.color = Color.parseColor("#8838BDF8")
        paint.strokeWidth = 4f
        for (i in 0..7) {
            val t = ((i * 12.5f + roadPhase) % 100f) / 100f
            val y1 = horizonY + (h - horizonY) * (t * t)
            val y2 = horizonY + (h - horizonY) * ((t + 0.055f).coerceAtMost(1f) * (t + 0.055f).coerceAtMost(1f))
            canvas.drawLine(cx, y1, cx, y2, paint)

            // Roadside trees & city silhouettes cleanly outside road edges
            val scale = 0.35f + t * 0.9f
            val treeXLeft = cx - w * (0.28f + t * 0.26f)
            val treeXRight = cx + w * (0.28f + t * 0.26f)
            drawTree(canvas, treeXLeft, y1, scale)
            drawTree(canvas, treeXRight, y1, scale)
            if (i % 2 == 0) {
                drawBuilding(canvas, cx - w * (0.40f + t * 0.20f), y1, scale)
                drawBuilding(canvas, cx + w * (0.40f + t * 0.20f), y1, scale)
            }
        }

        // 3. Soft Feathered Headlight Beams (Zero harsh edges!)
        val carCy = h * 0.50f
        val carW = w * 0.34f
        val carH = carW * 1.72f
        val carTop = carCy - carH * 0.5f
        val carBottom = carCy + carH * 0.5f

        if (headlightOn) {
            val beamLen = carH * 0.95f
            for (side in floatArrayOf(-0.28f, 0.28f)) {
                val bx = cx + carW * side
                paint.shader = RadialGradient(
                    bx, carTop + 8f, beamLen,
                    intArrayOf(Color.argb(180, 224, 242, 254), Color.argb(75, 125, 211, 252), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
                )
                val beamPath = Path().apply {
                    moveTo(bx - carW * 0.08f, carTop + 10f)
                    lineTo(bx + carW * 0.08f, carTop + 10f)
                    lineTo(bx + carW * 0.52f, carTop - beamLen)
                    lineTo(bx - carW * 0.52f, carTop - beamLen)
                    close()
                }
                canvas.drawPath(beamPath, paint)
            }
            paint.shader = null
        }

        // 4. Soft Radial Under-Car Shadow
        paint.shader = RadialGradient(
            cx, carCy + 10f, carH * 0.62f,
            Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawOval(cx - carW * 0.72f, carTop - 6f, cx + carW * 0.72f, carBottom + 18f, paint)
        paint.shader = null

        // 5. Sleek Pearl-White 3D Sedan Body
        val bodyRect = RectF(cx - carW * 0.5f, carTop, cx + carW * 0.5f, carBottom)
        paint.shader = LinearGradient(
            bodyRect.left, bodyRect.top, bodyRect.right, bodyRect.bottom,
            Color.parseColor("#FFFFFF"), Color.parseColor("#CBD5E1"), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bodyRect, carW * 0.26f, carW * 0.26f, paint)
        paint.shader = null

        // Panoramic Dark Glass Cabin
        val glassRect = RectF(
            cx - carW * 0.37f, carTop + carH * 0.24f,
            cx + carW * 0.37f, carBottom - carH * 0.20f
        )
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRoundRect(glassRect, carW * 0.16f, carW * 0.16f, paint)

        // Bright Red Brake Taillight Bar + Additive Bloom Glow
        val tailY = carBottom - carH * 0.085f
        if (handbrakeOn) {
            paint.shader = RadialGradient(
                cx, tailY + 6f, carW * 0.75f,
                Color.argb(165, 255, 40, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawOval(cx - carW * 0.7f, tailY - 26f, cx + carW * 0.7f, tailY + 42f, paint)
            paint.shader = null
        }
        paint.color = if (handbrakeOn) Color.parseColor("#FF1E1E") else Color.parseColor("#991B1B")
        canvas.drawRoundRect(cx - carW * 0.38f, tailY - 5f, cx + carW * 0.38f, tailY + 5f, 5f, 5f, paint)

        // 6. Rear Blue License Plate: "长生车机"
        val plateW = carW * 0.56f
        val plateH = plateW * 0.32f
        val plateTop = tailY + 10f
        val plateRect = RectF(cx - plateW * 0.5f, plateTop, cx + plateW * 0.5f, plateTop + plateH)
        paint.color = Color.parseColor("#1D4ED8")
        canvas.drawRoundRect(plateRect, 4f, 4f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawRoundRect(plateRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL
        textPaint.textSize = plateH * 0.62f
        canvas.drawText("长生车机", cx, plateTop + plateH * 0.72f, textPaint)
    }

    private fun drawTree(canvas: Canvas, x: Float, y: Float, s: Float) {
        paint.color = Color.parseColor("#134E4A")
        val p = Path().apply {
            moveTo(x, y - 34f * s)
            lineTo(x - 14f * s, y)
            lineTo(x + 14f * s, y)
            close()
        }
        canvas.drawPath(p, paint)
    }

    private fun drawBuilding(canvas: Canvas, x: Float, y: Float, s: Float) {
        paint.color = Color.parseColor("#111A2C")
        canvas.drawRect(x - 16f * s, y - 48f * s, x + 16f * s, y, paint)
        paint.color = Color.parseColor("#5538BDF8")
        canvas.drawRect(x - 10f * s, y - 36f * s, x + 10f * s, y - 32f * s, paint)
        canvas.drawRect(x - 10f * s, y - 22f * s, x + 10f * s, y - 18f * s, paint)
    }
}
