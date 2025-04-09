// src/main/java/com.example.hearalert/SettingsActivity.kt
package com.example.hearalert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.hearalert.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("hearalert_prefs", MODE_PRIVATE)
        val editor = prefs.edit()

        // Установка начального состояния чекбоксов
        binding.checkboxSiren.isChecked = prefs.getBoolean("class_8", true)
        binding.checkboxCry.isChecked = prefs.getBoolean("class_2", true)
        binding.checkboxDog.isChecked = prefs.getBoolean("class_3", true)
        binding.checkboxCar.isChecked = prefs.getBoolean("class_1", true)
        binding.checkboxStreetmusic.isChecked = prefs.getBoolean("class_9", true)
        binding.checkboxGunshot.isChecked = prefs.getBoolean("class_6", true)

        // Сохраняем изменения при клике
        binding.checkboxSiren.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_8", isChecked).apply()
        }
        binding.checkboxCry.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_2", isChecked).apply()
        }
        binding.checkboxDog.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_3", isChecked).apply()
        }
        binding.checkboxCar.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_1", isChecked).apply()
        }
        binding.checkboxStreetmusic.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_9", isChecked).apply()
        }
        binding.checkboxGunshot.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("class_6", isChecked).apply()
        }

        binding.backArrow.setOnClickListener {
            finish()
        }
    }
}