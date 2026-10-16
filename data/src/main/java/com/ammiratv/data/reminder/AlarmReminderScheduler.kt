package com.ammiratv.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ammiratv.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders through [AlarmManager]. Uses an inexact-while-idle alarm on purpose: it needs no
 * "exact alarm" permission and a delay of a few minutes does not matter for a reminder that
 * already fires [LEAD_TIME_MS] ahead of the programme.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    override fun schedule(
        channelName: String,
        programTitle: String,
        startTimeMs: Long,
        requestKey: String
    ): Boolean {
        val fireAt = startTimeMs - LEAD_TIME_MS
        if (fireAt <= System.currentTimeMillis()) return false

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_CHANNEL, channelName)
            putExtra(ReminderReceiver.EXTRA_TITLE, programTitle)
        }
        // Same key -> same PendingIntent, so asking twice for one programme does not duplicate it
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, requestKey.hashCode(), intent, flags)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
        return true
    }

    private companion object {
        const val LEAD_TIME_MS = 2 * 60 * 1000L
    }
}
