package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logo = findViewById<ImageView>(R.id.imgLogo)
        val appName = findViewById<TextView>(R.id.txtAppName)
        val tagline = findViewById<TextView>(R.id.txtTagline)

        // Initial state
        logo.alpha = 0f
        logo.scaleX = 0.80f
        logo.scaleY = 0.80f
        logo.translationY = 15f

        appName.alpha = 0f
        appName.translationY = 15f

        tagline.alpha = 0f
        tagline.translationY = 10f

        val smooth = AccelerateDecelerateInterpolator()

        // Logo animation
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(1400)
            .setInterpolator(smooth)
            .withEndAction {

                // App name
                appName.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(700)
                    .setInterpolator(smooth)
                    .withEndAction {

                        // Tagline
                        tagline.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(600)
                            .setInterpolator(smooth)
                            .withEndAction {

                                Handler(Looper.getMainLooper()).postDelayed({

                                    val intent = Intent(
                                        this@MainActivity,
                                        OnboardingActivity::class.java
                                    )

                                    startActivity(intent)

                                    overridePendingTransition(
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out
                                    )

                                    finish()

                                }, 600)
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }
}