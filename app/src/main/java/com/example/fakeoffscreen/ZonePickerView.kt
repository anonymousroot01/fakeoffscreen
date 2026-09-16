package com.example.fakeoffscreen

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ZonePickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var zoneXPercent = 0
    var zoneYPercent = 67
    var zoneWPercent = 100
    var zoneHPercent = 33

    private var zLeft = 0f
    private var zTop = 0f
    private var zRight = 0f
    private var zBottom = 0f

    private var dragMode = 0
    private var downX = 0f
    private var downY = 0f
    private var origLeft = 0f
    private var origTop = 0f
    private var origRight = 0f
    private var origBottom = 0f

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 255, 0)
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val paintHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.LEFT
    }

    private val handleRadius = 40f
    private val touchSlop = 60f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateZonePixels()
    }

    private fun updateZonePixels() {
        zLeft = width * zoneXPercent / 100f
        zTop = height * zoneYPercent / 100f
        zRight = zLeft + (width * zoneWPercent / 100f)
        zBottom = zTop + (height * zoneHPercent / 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.argb(220, 0, 0, 0))

        canvas.drawText("Yesil alani surukle / koselerden boyutlandir", 40f, 80f, paintText)
        canvas.drawText("X:$zoneXPercent Y:$zoneYPercent W:$zoneWPercent H:$zoneHPercent",
            40f, 140f, paintText)

        canvas.drawRect(zLeft, zTop, zRight, zBottom, paintFill)
        canvas.drawRect(zLeft, zTop, zRight, zBottom, paintBorder)

        canvas.drawCircle(zLeft, zTop, handleRadius, paintHandle)
        canvas.drawCircle(zRight, zTop, handleRadius, paintHandle)
        canvas.drawCircle(zLeft, zBottom, handleRadius, paintHandle)
        canvas.drawCircle(zRight, zBottom, handleRadius, paintHandle)

        val pad = minOf(zRight - zLeft, zBottom - zTop) / 10f
        val cellW = ((zRight - zLeft) - pad * 2) / 2f
        val cellH = ((zBottom - zTop) - pad * 2) / 2f
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            style = Paint.Style.FILL
        }
        for (r in 0..2) for (c in 0..2) {
            val cx = zLeft + pad + c * cellW
            val cy = zTop + pad + r * cellH
            canvas.drawCircle(cx, cy, 30f, nodePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                origLeft = zLeft; origTop = zTop
                origRight = zRight; origBottom = zBottom

                dragMode = when {
                    isNear(event.x, event.y, zLeft, zTop) -> 2
                    isNear(event.x, event.y, zRight, zTop) -> 3
                    isNear(event.x, event.y, zLeft, zBottom) -> 4
                    isNear(event.x, event.y, zRight, zBottom) -> 5
                    event.x in zLeft..zRight && event.y in zTop..zBottom -> 1
                    else -> 0
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                when (dragMode) {
                    1 -> {
                        var newLeft = origLeft + dx
                        var newTop = origTop + dy
                        val w = origRight - origLeft
                        val h = origBottom - origTop
                        if (newLeft < 0) newLeft = 0f
                        if (newTop < 0) newTop = 0f
                        if (newLeft + w > width) newLeft = width - w
                        if (newTop + h > height) newTop = height - h
                        zLeft = newLeft; zTop = newTop
                        zRight = zLeft + w; zBottom = zTop + h
                    }
                    2 -> {
                        zLeft = (origLeft + dx).coerceIn(0f, zRight - 200f)
                        zTop = (origTop + dy).coerceIn(0f, zBottom - 200f)
                    }
                    3 -> {
                        zRight = (origRight + dx).coerceIn(zLeft + 200f, width.toFloat())
                        zTop = (origTop + dy).coerceIn(0f, zBottom - 200f)
                    }
                    4 -> {
                        zLeft = (origLeft + dx).coerceIn(0f, zRight - 200f)
                        zBottom = (origBottom + dy).coerceIn(zTop + 200f, height.toFloat())
                    }
                    5 -> {
                        zRight = (origRight + dx).coerceIn(zLeft + 200f, width.toFloat())
                        zBottom = (origBottom + dy).coerceIn(zTop + 200f, height.toFloat())
                    }
                }
                savePercent()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = 0
                savePercent()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isNear(x: Float, y: Float, hx: Float, hy: Float): Boolean {
        return Math.hypot((x - hx).toDouble(), (y - hy).toDouble()) < touchSlop
    }

    private fun savePercent() {
        if (width == 0 || height == 0) return
        zoneXPercent = (zLeft / width * 100).toInt().coerceIn(0, 100)
        zoneYPercent = (zTop / height * 100).toInt().coerceIn(0, 100)
        zoneWPercent = ((zRight - zLeft) / width * 100).toInt().coerceIn(10, 100)
        zoneHPercent = ((zBottom - zTop) / height * 100).toInt().coerceIn(10, 100)
    }

    fun loadFrom(x: Int, y: Int, w: Int, h: Int) {
        zoneXPercent = x
        zoneYPercent = y
        zoneWPercent = w
        zoneHPercent = h
        post { updateZonePixels() }
    }
}
