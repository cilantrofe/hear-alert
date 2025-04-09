package com.example.hearalert

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.hearalert.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Обработчик нажатия на email
        binding.supportText.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support_hearalert@mail.ru")
                putExtra(Intent.EXTRA_SUBJECT, "Поддержка HearAlert")
            }
            startActivity(Intent.createChooser(intent, "Отправить email через:"))
        }

        binding.backArrow.setOnClickListener {
            finish() // Просто закрываем экран
        }
    }
}
