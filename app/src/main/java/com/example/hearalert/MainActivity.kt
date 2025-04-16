package com.example.hearalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hearalert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    // Биндинг для упрощенной работы с UI
    private lateinit var binding: ActivityMainBinding

    // Класс для работы с уведомлениями
    private lateinit var notificationHelper: NotificationHelper

    // Для проверки разрешений
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Разрешение на микрофон отклонено!", Toast.LENGTH_SHORT).show()
            binding.switch3.isChecked = false
        } else {
            startAudioService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Иницилизируем классы помощники
        notificationHelper = NotificationHelper(this)

        // Запрос разрешения на уведомления
        notificationHelper.requestNotificationPermission()

        // Открытие канала уведомлений
        notificationHelper.createNotificationChannel()

        // Запуск непрерывной записи по нажатию свитча
        binding.switch3.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    binding.switch3.isChecked = false // Откат если нет разрешения
                } else {
                    startAudioService()
                }
            } else {
                stopAudioService()
            }
        }

        binding.aboutButton.setOnClickListener {
            // Переход на экран About
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        binding.settingsButton.setOnClickListener {
            // Переход на экран настроек
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "Фоновое прослушивание включено"
        Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopAudioService() {
        val intent = Intent(this, AudioForegroundService::class.java)
        stopService(intent)
        binding.statusText.text = "Фоновое прослушивание выключено"
        Toast.makeText(this, "Сервис остановлен", Toast.LENGTH_SHORT).show()
    }
}
