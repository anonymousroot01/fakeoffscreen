package com.example.fakeoffscreen

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class BlackScreenService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var nm: NotificationManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var overlay: View? = null
    private var pattern: List<Int> = emptyList()
    private val input = mutableListOf<Int>()

    private var screenW = 0
    private var screenH = 0

    // Desen bölgesi
    private var zoneLeft = 0f
    private var zoneTop = 0f
    private var zoneW = 0f
    private var zoneH = 0f

    private val nodes = Array(3) { Array(3) { PointF() } }
    private var nodeRadiusPx = 140f

    private var volumeUpCount = 0
    private var lastVolumeUpTime = 0L
    private var previousInterruptionFilter = -1
    private var kioskActive = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    overlay?.visibility = View.INVISIBLE
                }
                Intent.ACTION_SCREEN_ON -> {
                    repeat(5) { delay ->
                        Handler(Looper.getMainLooper()).postDelayed({
                            overlay?.visibility = View.VISIBLE
                            overlay?.requestFocus()
                            applyKioskMode()
                        }, (delay * 100).toLong())
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    overlay?.visibility = View.VISIBLE
                    overlay?.requestFocus()
                    applyKioskMode()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        val saved = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("pattern", "") ?: ""
        pattern = saved.split(",").mapNotNull { it.toIntOrNull() }

        val ch = "fake_off"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ch, "FakeOff", NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        startForeground(1, NotificationCompat.Builder(this, ch)
            .setContentTitle("Ekran kapalı")
            .setContentText("Gizli desen veya ses açma 5x")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build())

        enableDnd()

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        loadZoneFromPrefs()
        showOverlay()
    }

    private fun loadZoneFromPrefs() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val wPct = prefs.getInt("zoneWidth", 100) / 100f
        val hPct = prefs.getInt("zoneHeight", 33) / 100f
        val xPct = prefs.getInt("zoneX", 0) / 100f
        val yPct = prefs.getInt("zoneY", 67) / 100f

        zoneW = screenW * wPct
        zoneH = screenH * hPct
        zoneLeft = screenW * xPct
        zoneTop = screenH * yPct

        val pad = minOf(zoneW, zoneH) / 10f
        val cellW = (zoneW - pad * 2) / 2f
        val cellH = (zoneH - pad * 2) / 2f
        for (r in 0..2) for (c in 0..2)
            nodes[r][c] = PointF(
                zoneLeft + pad + c * cellW,
                zoneTop + pad + r * cellH
            )

        nodeRadiusPx = minOf(cellW, cellH) * 0.6f
        if (nodeRadiusPx < 80f) nodeRadiusPx = 80f
    }

    // ============ DND ============
    private fun enableDnd() {
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                previousInterruptionFilter = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        } catch (_: Exception) {}
    }

    private fun disableDnd() {
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    if (previousInterruptionFilter != -1) previousInterruptionFilter
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        } catch (_: Exception) {}
    }

    // ============ KIOSK MODE ============
    private fun applyKioskMode() {
        if (!dpm.isAdminActive(adminComponent)) return
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            kioskActive = true
        } catch (_: Exception) {}
    }

    private fun removeKioskMode() {
        if (!dpm.isAdminActive(adminComponent)) return
        try {
            dpm.setLockTaskPackages(adminComponent, emptyArray())
            kioskActive = false
        } catch (_: Exception) {}
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
        v.isFocusable = true
        v.isFocusableInTouchMode = true

        @Suppress("DEPRECATION")
        v.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.OPAQUE
        )
        p.screenBrightness = 0.0f

        // Sürekli bar gizle - anonim Runnable
        val hideBarsRunnable = object : Runnable {
            override fun run() {
                @Suppress("DEPRECATION")
                v.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
                Handler(Looper.getMainLooper()).postDelayed(this, 300)
            }
        }
        Handler(Looper.getMainLooper()).post(hideBarsRunnable)

        v.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (event.action == KeyEvent.ACTION_DOWN) handleVolumeUp()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return@setOnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_BACK) return@setOnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_HOME) return@setOnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) return@setOnKeyListener true
            false
        }

        v.setOnTouchListener { _, e ->
            handleTouch(e)
            true
        }

        v.requestFocus()
        wm.addView(v, p)
        overlay = v

        applyKioskMode()
    }

    private fun handleVolumeUp() {
        val now = System.currentTimeMillis()
        if (now - lastVolumeUpTime < 800) volumeUpCount++ else volumeUpCount = 1
        lastVolumeUpTime = now
        if (volumeUpCount >= 5) {
            volumeUpCount = 0
            unlock()
        }
    }

    private fun handleTouch(e: MotionEvent) {
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
    }

    private fun findNode(x: Float, y: Float): Int? {
        if (x < zoneLeft || x > zoneLeft + zoneW) return null
        if (y < zoneTop || y > zoneTop + zoneH) return null
        for (r in 0..2) for (c in 0..2) {
            val p = nodes[r][c]
            if (hypot((x - p.x).toDouble(), (y - p.y).toDouble()) < nodeRadiusPx)
                return r * 3 + c
        }
        return null
    }

    private fun unlock() {
        removeKioskMode()
        disableDnd()
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        stopSelf()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY

    override fun onDestroy() {
        removeKioskMode()
        disableDnd()
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
