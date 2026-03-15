package com.example.aiadventchallenge.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.aiadventchallenge.MainActivity
import com.example.aiadventchallenge.domain.agent.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Планирование напоминаний через AlarmManager (День 18).
 * Используется setAlarmClock — срабатывание в точное время (как будильник), иконка в статус-баре.
 * После перезагрузки устройства напоминания не восстанавливаются.
 */
class AppReminderScheduler(private val context: Context) : ReminderScheduler {

  override fun scheduleReminder(inMinutes: Int, message: String) {
    if (inMinutes <= 0) {
      Log.w(LOG_TAG, "scheduleReminder: inMinutes=$inMinutes <= 0, skip")
      return
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    if (alarmManager == null) {
      Log.e(LOG_TAG, "scheduleReminder: AlarmManager is null")
      return
    }
    val triggerAt = System.currentTimeMillis() + inMinutes * 60_000L
    val requestId = (System.currentTimeMillis() and 0x7FFF).toInt()
    val triggerAtFormatted = TRIGGER_DATE_FMT.format(Date(triggerAt))
    Log.d(LOG_TAG, "scheduleReminder: inMinutes=$inMinutes, message=\"$message\", requestId=$requestId, triggerAt=$triggerAt ($triggerAtFormatted)")
    val intent = Intent(context, ReminderReceiver::class.java).apply {
      action = ReminderReceiver.ACTION_REMINDER
      putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
      putExtra(ReminderReceiver.EXTRA_REQUEST_ID, requestId)
    }
    val broadcastPending = PendingIntent.getBroadcast(
      context,
      requestId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val showIntent = PendingIntent.getActivity(
      context,
      requestId,
      Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
    try {
      alarmManager.setAlarmClock(alarmClockInfo, broadcastPending)
      Log.d(LOG_TAG, "scheduleReminder: using setAlarmClock (exact time, like system alarm)")
      Log.i(LOG_TAG, "scheduleReminder: alarm scheduled successfully for $triggerAtFormatted")
    } catch (e: SecurityException) {
      Log.w(LOG_TAG, "scheduleReminder: setAlarmClock denied (SCHEDULE_EXACT_ALARM?), fallback to inexact", e)
      scheduleInexactFallback(alarmManager, triggerAt, broadcastPending, triggerAtFormatted)
    } catch (e: Exception) {
      Log.e(LOG_TAG, "scheduleReminder: Failed to schedule reminder", e)
    }
  }

  private fun scheduleInexactFallback(
    alarmManager: AlarmManager,
    triggerAt: Long,
    pending: PendingIntent,
    triggerAtFormatted: String
  ) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      } else {
        @Suppress("DEPRECATION")
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      }
      Log.i(LOG_TAG, "scheduleReminder: fallback (inexact) scheduled for $triggerAtFormatted")
    } catch (e: Exception) {
      Log.e(LOG_TAG, "scheduleReminder: fallback also failed", e)
    }
  }

  companion object {
    private const val LOG_TAG = "AppReminderScheduler"
    private val TRIGGER_DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
  }
}
