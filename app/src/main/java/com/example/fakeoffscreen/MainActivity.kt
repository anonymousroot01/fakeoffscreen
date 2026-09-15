package com.example.fakeoffscreen

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 1) Overlay İzni
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "Overlay izni zaten var", Toast.LENGTH_SHORT).show()
            }
        }

        // 2) DND İzni
        findViewById<Button>(R.id.btnDndPermission).setOnClickListener {
            if (!nm.isNotificationPolicyAccessGranted) {
                // DND erişim ayarlarına yönlendir
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(
                    this,
                    "FakeOffScreen'i bulup izin ver",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "DND izni zaten var", Toast.LENGTH_SHORT).show()
            }
        }

        // 3) Desen Ayarla
        findViewById<Button>(R.id.btnSetPattern).setOnClickListener {
            startActivity(Intent(this, PatternSetupActivity::class.java))
        }

        // 4) Başlat
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Önce overlay izni ver", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (prefs.getString("pattern", null) == null) {
                Toast.makeText(this, "Önce desen ayarla", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!nm.isNotificationPolicyAccessGranted) {
                Toast.makeText(
                    this,
                    "DND izni yok — bildirimler görünebilir",
                    Toast.LENGTH_LONG
                ).show()
            }
            startForegroundService(Intent(this, BlackScreenService::class.java))
            Toast.makeText(this, "Başladı", Toast.LENGTH_SHORT).show()
        }

        // Durdur
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, BlackScreenService::class.java))
            Toast.makeText(this, "Durduruldu", Toast.LENGTH_SHORT).show()
        }
    }
}
