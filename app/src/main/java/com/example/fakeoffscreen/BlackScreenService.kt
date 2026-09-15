package com.example.fakeoffscreen

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class BlackScreenService : Service() {

    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var pattern: List<Int> = emptyList()
    private val input = mutableListOf<Int>()

    private val nodes = Array(3) { Array(3) { PointF() } }
    private var zoneLeft = 0f
    private var zoneTop = 0f
    private var zoneW = 0f
    private var zoneH = 0f

    private var powerCount = 0
    private var lastPower = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val now = System.currentTimeMillis()
                    if (now - lastPower < 1500) powerCount++ else powerCount = 1
                    lastPower = now
                    if (powerCount >= 5) {
                        powerCount = 0
                        unlock()
                    }
                    if (overlay == null) showOverlay()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (overlay == null) showOverlay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val saved = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("pattern", "") ?: ""
        pattern = saved.split(",").mapNotNull { it.toIntOrNull() }

        val ch = "fake_off"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ch, "FakeOff", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        startForeground(
            1,
            NotificationCompat.Builder(this, ch)
                .setContentTitle("Ekran kapalı")
                .setContentText("Deseni çiz veya güç tuşu 5x")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        )

        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )

        showOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return

        val v = object : View(this) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                canvas.drawColor(Color.BLACK)
            }
        }
        v.setBackgroundColor(Color.BLACK)
        v.isClickable = true

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        v.post {
            zoneW = v.width / 2f
            zoneH = v.height / 2f
            zoneLeft = v.width - zoneW
            zoneTop = v.height - zoneH

            val pad = zoneW / 8f
            val cw = (zoneW - pad * 2) / 2f
            val chh = (zoneH - pad * 2) / 2f
            for (r in 0..2) for (c in 0..2)
                nodes[r][c] = PointF(
                    zoneLeft + pad + c * cw,
                    zoneTop + pad + r * chh
                )
        }

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> input.clear()
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val idx = findNode(e.x, e.y)
                    if (idx != null && !input.contains(idx)) input.add(idx)
                    if (e.action == MotionEvent.ACTION_UP) {
                        if (input == pattern && input.isNotEmpty()) unlock()
                        input.clear()
                    }
                }
            }
            true
        }

        wm.addView(v, p)
        overlay = v
    }

    private fun findNode(x: Float, y: Float): Int? {
        if (x < zoneLeft || y < zoneTop) return null
        for (r in 0..2) for (c in 0..2) {
            val p = nodes[r][c]
            if (hypot((x - p.x).toDouble(), (y - p.y).toDouble()) < 60f)
                return r * 3 + c
        }
        return null
    }

    private fun unlock() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        stopSelf()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY

    override fun onDestroy() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
