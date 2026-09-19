package com.example.fakeoffscreen

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val accepted = prefs.getBoolean("disclaimer_accepted", false)

            val next = if (accepted) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, DisclaimerActivity::class.java)
            }
            startActivity(next)
            finish()
        }, 1500)  // 1.5 saniye splash
    }
}
