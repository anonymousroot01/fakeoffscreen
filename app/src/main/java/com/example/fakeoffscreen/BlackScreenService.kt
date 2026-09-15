package com.example.fakeoffscreen

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class BlackScreenService : Service() {

    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var pattern: List<Int> = emptyList()
    private val input = mutableListOf<Int>()

    // Ekran ölçüleri
    private var screenW = 0
    private var screenH = 0

    // Gizli desen bölgesi: alt 1/3
    private var zoneLeft = 0f
    private var zoneTop = 0f
    private var zoneW = 0f
    private var zoneH = 0f

    // 3x3 noktalar
    private val nodes = Array(3) { Array(3) { PointF() } }
    private val nodeRadiusPx = 120f

    // Kombinasyon algılama
    private var volumeUpPressed = false
    private var powerPressed = false
    private var comboHandled = false

    // Ses tuşu yakalama
    private var volumeUpReceiver: BroadcastReceiver? = null

    // Ekran receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Ekran kapandı, ama overlay duruyor
                    // Hemen tekrar göster (ekran gelince siyah olsun)
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (overlay == null) showOverlay()
                    }, 100)
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

        // Ekran boyutları
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        // Deseni yükle
        val saved = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("pattern", "") ?: ""
        pattern = saved.split(",").mapNotNull { it.toIntOrNull() }

        // Bildirim
        val ch = "fake_off"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ch, "FakeOff", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        startForeground(1, NotificationCompat.Builder(this, ch)
            .setContentTitle("Ekran kapalı")
            .setContentText("Ses açma + güç ile aç")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build())

        // Ekran olaylarını dinle
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        // Ses tuşlarını yakalamaya çalış (kısmen çalışır)
        setupVolumeKeyBlock()

        showOverlay()
    }

    private fun setupVolumeKeyBlock() {
        // Not: Android 10'da ses tuşlarını global engellemek imkansız
        // Ama overlay açıkken MediaSession ile "ses değişmesin" diyebiliriz
        // Şimdilik sadece overlay'e odak vererek ses barını engellemeye çalışacağız
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

        // Sistem çubuklarını gizle
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
                or WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.OPAQUE
        )

        // Desen bölgesini ekran boyutundan hesapla
        zoneW = screenW.toFloat()
        zoneH = screenH / 3f           // alt 1/3
        zoneLeft = 0f
        zoneTop = screenH - zoneH      // alt 1/3'ün başlangıcı

        // 3x3 noktaları yerleştir
        val pad = zoneW / 10f
        val cellW = (zoneW - pad * 2) / 2f
        val cellH = (zoneH - pad * 2) / 2f
        for (r in 0..2) for (c in 0..2)
            nodes[r][c] = PointF(
                zoneLeft + pad + c * cellW,
                zoneTop + pad + r * cellH
            )

        v.setOnTouchListener { _, e ->
            handleTouch(e)
            true
        }

        v.setOnKeyListener { _, keyCode, event ->
            handleKeyEvent(keyCode, event)
            true
        }

        v.requestFocus()

        wm.addView(v, p)
        overlay = v
    }

    private fun handleTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                input.clear()
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val idx = findNode(e.x, e.y)
                if (idx != null && !input.contains(idx)) input.add(idx)
                if (e.action == MotionEvent.ACTION_UP) {
                    if (input == pattern && input.isNotEmpty()) {
                        unlock()
                    }
                    input.clear()
                }
            }
        }
    }

    private fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        // Ses tuşları
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = (event.action == KeyEvent.ACTION_DOWN)
            if (event.action == KeyEvent.ACTION_DOWN) checkCombo()
            // true döndürerek ses barını engelle
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true  // ses barını engelle
        }
        return false
    }

    private fun checkCombo() {
        // Bu fonksiyon ses açma basılıyken çağrılır
        // Güç tuşu algılaması SCREEN_OFF/ON ile yapılır
        // Kombinasyon: son 2 saniye içinde SCREEN_OFF olduysa + ses açma basılıysa
        if (volumeUpPressed && (System.currentTimeMillis() - lastPowerTime) < 2000) {
            unlock()
        }
    }

    private var lastPowerTime = 0L

    private fun findNode(x: Float, y: Float): Int? {
        // Sadece gizli bölgede mi?
        if (y < zoneTop || y > zoneTop + zoneH) return null
        for (r in 0..2) for (c in 0..2) {
            val p = nodes[r][c]
            if (hypot((x - p.x).toDouble(), (y - p.y).toDouble()) < nodeRadiusPx)
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
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
