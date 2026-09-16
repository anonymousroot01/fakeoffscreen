package com.example.fakeoffscreen

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val picker = findViewById<ZonePickerView>(R.id.zonePicker)

        picker.loadFrom(
            prefs.getInt("zoneX", 0),
            prefs.getInt("zoneY", 67),
            prefs.getInt("zoneWidth", 100),
            prefs.getInt("zoneHeight", 33)
        )

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putInt("zoneX", picker.zoneXPercent)
                .putInt("zoneY", picker.zoneYPercent)
                .putInt("zoneWidth", picker.zoneWPercent)
                .putInt("zoneHeight", picker.zoneHPercent)
                .apply()
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }
}
