package com.example.aiadventchallenge.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aiadventchallenge.MainActivity
import com.example.aiadventchallenge.R

/**
 * Получает срабатывание AlarmManager и показывает уведомление-напоминание (День 18).
 */
class ReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_REMINDER) return
    val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Напоминание"
    ensureChannel(context)
    val openIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    val pendingOpen = PendingIntent.getActivity(
      context,
      intent.getIntExtra(EXTRA_REQUEST_ID, 0),
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText(message)
      .setContentIntent(pendingOpen)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()
    val nm = NotificationManagerCompat.from(context)
    try {
      nm.notify(intent.getIntExtra(EXTRA_REQUEST_ID, 0), notification)
    } catch (_: SecurityException) { }
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
      )
      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)
    }
  }

  companion object {
    const val ACTION_REMINDER = "com.example.aiadventchallenge.REMINDER_ALARM"
    const val CHANNEL_ID = "reminders"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_REQUEST_ID = "request_id"
  }
}
