package com.example.fakeoffscreen

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class PatternSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_setup)

        val patternView = findViewById<PatternView>(R.id.patternView)
        patternView.onPatternComplete = { pattern ->
            if (pattern.size < 4) {
                Toast.makeText(this, "En az 4 nokta seç", Toast.LENGTH_SHORT).show()
                patternView.reset()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Deseni Kaydet")
                    .setMessage("Desen: ${pattern.joinToString("-")}\n\nBu deseni hatırla.")
                    .setPositiveButton("Kaydet") { _, _ ->
                        getSharedPreferences("settings", MODE_PRIVATE)
                            .edit()
                            .putString("pattern", pattern.joinToString(","))
                            .apply()
                        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .setNegativeButton("İptal") { _, _ -> patternView.reset() }
                    .show()
            }
        }
    }
}
