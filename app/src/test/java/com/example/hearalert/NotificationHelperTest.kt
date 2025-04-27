package com.example.hearalert

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class NotificationHelperUnitTest {

    private lateinit var context: Context
    private lateinit var notificationHelper: NotificationHelper

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        notificationHelper = NotificationHelper(context)
    }

    @Test
    fun createNotificationChannel_doesNotCrash() {
        val notificationManager = mock(NotificationManager::class.java)
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(
            notificationManager
        )

        notificationHelper.createNotificationChannel()
        verify(context).getSystemService(Context.NOTIFICATION_SERVICE)
    }

    @Test
    fun showPushNotification_doesNotCrash() {
        val notificationManagerCompat = mock(NotificationManagerCompat::class.java)
        notificationHelper.showPushNotification("Test Title", 1)
    }
}
