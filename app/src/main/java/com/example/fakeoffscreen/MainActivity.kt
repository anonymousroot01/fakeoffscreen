package com.example.fakeoffscreen

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
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
                Toast.makeText(this, "Overlay izni var", Toast.LENGTH_SHORT).show()
            }
        }

        // 2) Admin İzni
        findViewById<Button>(R.id.btnAdminPermission).setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Kiosk modu için gerekli"
                    )
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Admin izni var", Toast.LENGTH_SHORT).show()
            }
        }

        // 3) DND İzni
        findViewById<Button>(R.id.btnDndPermission).setOnClickListener {
            if (!nm.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(
                    this,
                    "FakeOffScreen'i bul ve izin ver",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "DND izni var", Toast.LENGTH_SHORT).show()
            }
        }

        // 4) Deseni Ayarla
        findViewById<Button>(R.id.btnSetPattern).setOnClickListener {
            startActivity(Intent(this, PatternSetupActivity::class.java))
        }

        // 5) Desen Alanı Ayarla
        findViewById<Button>(R.id.btnZoneSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 6) Ekranı Karart (Activity başlat)
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Admin izni ver", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (prefs.getString("pattern", null) == null) {
                Toast.makeText(this, "Desen ayarla", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, BlackScreenActivity::class.java))
            finish()
        }
    }
}
