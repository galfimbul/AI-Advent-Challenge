package com.example.aiadventchallenge.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.aiadventchallenge.domain.agent.ReminderScheduler

/**
 * Планирование напоминаний через AlarmManager (День 18).
 * Точное время срабатывания; после перезагрузки устройства напоминания не восстанавливаются.
 */
class AppReminderScheduler(private val context: Context) : ReminderScheduler {

  override fun scheduleReminder(inMinutes: Int, message: String) {
    if (inMinutes <= 0) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val triggerAt = System.currentTimeMillis() + inMinutes * 60_000L
    val requestId = (System.currentTimeMillis() and 0x7FFF).toInt()
    val intent = Intent(context, ReminderReceiver::class.java).apply {
      action = ReminderReceiver.ACTION_REMINDER
      putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
      putExtra(ReminderReceiver.EXTRA_REQUEST_ID, requestId)
    }
    val pending = PendingIntent.getBroadcast(
      context,
      requestId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      } else {
        @Suppress("DEPRECATION")
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      }
    } catch (e: Exception) {
      Log.e(LOG_TAG, "Failed to schedule reminder", e)
    }
  }

  companion object {
    private const val LOG_TAG = "AppReminderScheduler"
  }
}
