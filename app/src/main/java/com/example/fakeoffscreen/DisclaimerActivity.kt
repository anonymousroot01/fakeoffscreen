package com.example.fakeoffscreen

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DisclaimerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disclaimer)

        findViewById<Button>(R.id.btnAccept).setOnClickListener {
            getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putBoolean("disclaimer_accepted", true)
                .putLong("disclaimer_accepted_at", System.currentTimeMillis())
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnDecline).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Uyarı")
                .setMessage(R.string.decline_message)
                .setPositiveButton("Tamam") { _, _ ->
                    finishAffinity()  // Uygulamayı tamamen kapat
                }
                .setCancelable(false)
                .show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Geri tuşuyla çıkamasın
        Toast.makeText(this, "Kabul etmeden devam edemezsiniz", Toast.LENGTH_SHORT).show()
    }
}
