package com.example.aiadventchallenge.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aiadventchallenge.MainActivity
import com.example.aiadventchallenge.R

/**
 * Получает срабатывание AlarmManager и показывает уведомление-напоминание (День 18).
 */
class ReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(LOG_TAG, "onReceive: action=${intent.action}")
    if (intent.action != ACTION_REMINDER) {
      Log.w(LOG_TAG, "onReceive: ignoring unknown action")
      return
    }
    val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Напоминание"
    val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, 0)
    Log.i(LOG_TAG, "onReceive: showing reminder requestId=$requestId, message=\"$message\"")
    ensureChannel(context)
    val openIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    val pendingOpen = PendingIntent.getActivity(
      context,
      requestId,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val shortPreview = if (message.length > 50) message.take(47) + "…" else message
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(context.getString(R.string.reminder_channel_name))
      .setContentText(shortPreview)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setContentIntent(pendingOpen)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()
    val nm = NotificationManagerCompat.from(context)
    try {
      nm.notify(requestId, notification)
      Log.i(LOG_TAG, "onReceive: notification shown for requestId=$requestId")
    } catch (e: SecurityException) {
      Log.e(LOG_TAG, "onReceive: SecurityException when showing notification (permission POST_NOTIFICATIONS?)", e)
    }
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Log.d(LOG_TAG, "ensureChannel: creating channel $CHANNEL_ID")
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
    private const val LOG_TAG = "ReminderReceiver"
    const val ACTION_REMINDER = "com.example.aiadventchallenge.REMINDER_ALARM"
    const val CHANNEL_ID = "reminders"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_REQUEST_ID = "request_id"
  }
}
