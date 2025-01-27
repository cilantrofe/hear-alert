// src/main/java/com.example.hearalert/SplashActivity.kt
package com.example.hearalert

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoImageView: ImageView = findViewById(R.id.splash_logo)
        val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_animation)
        logoImageView.startAnimation(scaleAnimation)

        // Задержка перед переходом на основной экран
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000) // 3 секунды
    }
}
