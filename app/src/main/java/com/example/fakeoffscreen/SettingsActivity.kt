package com.example.fakeoffscreen

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // Slider'lar
        val sbWidth = findViewById<SeekBar>(R.id.sbWidth)
        val sbHeight = findViewById<SeekBar>(R.id.sbHeight)
        val sbX = findViewById<SeekBar>(R.id.sbX)
        val sbY = findViewById<SeekBar>(R.id.sbY)

        val tvWidth = findViewById<TextView>(R.id.tvWidth)
        val tvHeight = findViewById<TextView>(R.id.tvHeight)
        val tvX = findViewById<TextView>(R.id.tvX)
        val tvY = findViewById<TextView>(R.id.tvY)

        // Kayıtlı değerleri yükle
        sbWidth.progress = prefs.getInt("zoneWidth", 100)
        sbHeight.progress = prefs.getInt("zoneHeight", 33)
        sbX.progress = prefs.getInt("zoneX", 0)
        sbY.progress = prefs.getInt("zoneY", 67)

        fun updateLabels() {
            tvWidth.text = "Genişlik: %${sbWidth.progress}"
            tvHeight.text = "Yükseklik: %${sbHeight.progress}"
            tvX.text = "X Konumu: %${sbX.progress}"
            tvY.text = "Y Konumu: %${sbY.progress}"
        }
        updateLabels()

        sbWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sbHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sbX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sbY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putInt("zoneWidth", sbWidth.progress)
                .putInt("zoneHeight", sbHeight.progress)
                .putInt("zoneX", sbX.progress)
                .putInt("zoneY", sbY.progress)
                .apply()
            finish()
        }
    }
}
