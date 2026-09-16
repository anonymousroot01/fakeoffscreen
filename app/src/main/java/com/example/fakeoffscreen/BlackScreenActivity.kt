package com.example.fakeoffscreen

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class BlackScreenActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var nm: NotificationManager

    private var pattern: List<Int> = emptyList()
    private val input = mutableListOf<Int>()

    private var screenW = 0
    private var screenH = 0

    private var zoneLeft = 0f
    private var zoneTop = 0f
    private var zoneW = 0f
    private var zoneH = 0f

    private val nodes = Array(3) { Array(3) { PointF() } }
    private var nodeRadiusPx = 140f

    private var volumeUpCount = 0
    private var lastVolumeUpTime = 0L

    private var isLocked = false
    private var isUnlocking = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Hiçbir şey yapma
                }
                Intent.ACTION_SCREEN_ON -> {
                    val delays = longArrayOf(0, 50, 150, 300, 600, 1000, 1500, 2000)
                    for (d in delays) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            forceRecover()
                        }, d)
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    forceRecover()
                    Handler(Looper.getMainLooper()).postDelayed({
                        forceRecover()
                    }, 200)
                }
            }
        }
    }

    private fun forceRecover() {
        if (isUnlocking) return
        try {
            val intent = Intent(this, BlackScreenActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            startActivity(intent)

            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            if (!am.isInLockTaskMode) {
                try {
                    if (dpm.isAdminActive(adminComponent)) {
                        dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
                        dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                    }
                    startLockTask()
                    isLocked = true
                } catch (_: Exception) {
                    try { startLockTask(); isLocked = true } catch (_: Exception) {}
                }
            }

            hideSystemBars()
            window.decorView.requestFocus()
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContentView(R.layout.activity_black_screen)

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        val saved = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("pattern", "") ?: ""
        pattern = saved.split(",").mapNotNull { it.toIntOrNull() }

        loadZoneFromPrefs()

        hideSystemBars()
        enableDnd()
        enterLockTask()

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        val root = findViewById<View>(R.id.rootLayout)
        root.setBackgroundColor(Color.BLACK)
        root.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        root.isFocusableInTouchMode = true
        root.requestFocus()

        // Bar gizleme + watchdog
        val hideBarsRunnable = object : Runnable {
            override fun run() {
                if (!isUnlocking) {
                    hideSystemBars()
                    try {
                        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                        if (!am.isInLockTaskMode) {
                            forceRecover()
                        }
                    } catch (_: Exception) {}
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 300)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(hideBarsRunnable, 300)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
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

    private fun enterLockTask() {
        if (!dpm.isAdminActive(adminComponent)) {
            try { startLockTask(); isLocked = true } catch (_: Exception) {}
            return
        }
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            startLockTask()
            isLocked = true
        } catch (_: Exception) {
            try { startLockTask(); isLocked = true } catch (_: Exception) {}
        }
    }

    private fun exitLockTask() {
        try {
            if (isLocked) {
                stopLockTask()
                isLocked = false
            }
            if (dpm.isAdminActive(adminComponent)) {
                dpm.setLockTaskPackages(adminComponent, emptyArray())
            }
        } catch (_: Exception) {}
    }

    private fun enableDnd() {
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        } catch (_: Exception) {}
    }

    private fun disableDnd() {
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (_: Exception) {}
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

    private fun handleVolumeUp() {
        val now = System.currentTimeMillis()
        if (now - lastVolumeUpTime < 800) volumeUpCount++ else volumeUpCount = 1
        lastVolumeUpTime = now
        if (volumeUpCount >= 5) {
            volumeUpCount = 0
            unlock()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            handleVolumeUp()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        if (keyCode == KeyEvent.KEYCODE_HOME) return true
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) return true
        if (keyCode == KeyEvent.KEYCODE_MENU) return true
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isUnlocking) {
            forceRecover()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isUnlocking) {
            Handler(Looper.getMainLooper()).postDelayed({
                forceRecover()
            }, 100)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!isUnlocking) {
            Handler(Looper.getMainLooper()).postDelayed({
                forceRecover()
            }, 100)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isUnlocking) {
            hideSystemBars()
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (!am.isInLockTaskMode) {
                    forceRecover()
                }
            } catch (_: Exception) {}
        }
    }

    private fun unlock() {
        isUnlocking = true
        exitLockTask()
        disableDnd()
        finish()
    }

    override fun onDestroy() {
        if (!isUnlocking) {
            isUnlocking = true
        }
        exitLockTask()
        disableDnd()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
