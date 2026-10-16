package com.ammiratv.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Shows the "programme is about to start" notification; tapping it opens the app. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val channelName = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Promemoria programmi", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            PendingIntent.getActivity(context, 0, it, flags)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Sta per iniziare su $channelName")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        // Unique id per programme so several reminders can coexist
        manager.notify((channelName + title).hashCode(), notification)
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "reminders"
    }
}
