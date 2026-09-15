package com.example.fakeoffscreen

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class PatternView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val nodes = Array(3) { Array(3) { PointF() } }
    private val nodeRadius = 40f
    private var selected = mutableListOf<Int>()

    private val paintNode = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY; style = Paint.Style.FILL
    }
    private val paintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; style = Paint.Style.FILL
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; strokeWidth = 8f; style = Paint.Style.STROKE
    }
    private val path = Path()
    private var currentX = 0f
    private var currentY = 0f

    var onPatternComplete: ((List<Int>) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = nodeRadius * 2
        val cellW = (w - pad * 2) / 2f
        val cellH = (h - pad * 2) / 2f
        for (r in 0..2) for (c in 0..2)
            nodes[r][c] = PointF(pad + c * cellW, pad + r * cellH)
    }

    override fun onDraw(canvas: Canvas) {
        for (r in 0..2) for (c in 0..2) {
            val idx = r * 3 + c
            val p = nodes[r][c]
            canvas.drawCircle(
                p.x, p.y, nodeRadius,
                if (selected.contains(idx)) paintSelected else paintNode
            )
        }
        path.reset()
        if (selected.isNotEmpty()) {
            val f = nodes[selected[0] / 3][selected[0] % 3]
            path.moveTo(f.x, f.y)
            for (i in 1 until selected.size) {
                val n = nodes[selected[i] / 3][selected[i] % 3]
                path.lineTo(n.x, n.y)
            }
            if (currentX > 0) path.lineTo(currentX, currentY)
            canvas.drawPath(path, paintLine)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                currentX = event.x; currentY = event.y
                val idx = findNode(event.x, event.y)
                if (idx != null && !selected.contains(idx)) selected.add(idx)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentX = 0f
                if (selected.isNotEmpty()) onPatternComplete?.invoke(selected.toList())
                invalidate()
            }
        }
        return true
    }

    private fun findNode(x: Float, y: Float): Int? {
        for (r in 0..2) for (c in 0..2) {
            val p = nodes[r][c]
            if (Math.hypot((x - p.x).toDouble(), (y - p.y).toDouble()) < nodeRadius * 1.5)
                return r * 3 + c
        }
        return null
    }

    fun reset() {
        selected.clear()
        invalidate()
    }
}
