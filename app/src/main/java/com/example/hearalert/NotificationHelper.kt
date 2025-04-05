// NotificationHelper.kt
package com.example.hearalert

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Класс для отправки уведомлений
 */
class NotificationHelper(private val context: Context) {
    private val classLabels = mapOf(
        1 to "Автомобильный гудок",
        2 to "Дети играют",
        3 to "Лай собаки",
        6 to "Выстрел",
        8 to "Сирена",
        9 to "Уличная музыка"
    )

    private val notificationPermissionLauncher =
        (context as? androidx.activity.ComponentActivity)?.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Уведомления отключены!", Toast.LENGTH_SHORT).show()
            }
        }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "HearAlert Notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления при обнаружении важных звуков"
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showPushNotification(title: String, predictedClass: Int) {
        val message = classLabels[predictedClass] ?: "Неизвестный звук"

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title)
            .setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        private const val CHANNEL_ID = "hear_alert_channel"
    }
}
