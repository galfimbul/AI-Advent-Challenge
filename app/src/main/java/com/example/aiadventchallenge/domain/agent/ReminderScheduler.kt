package com.example.aiadventchallenge.domain.agent

/**
 * Планирование локального уведомления-напоминания на устройстве (AlarmManager).
 * Реализация в app — AppReminderScheduler.
 */
interface ReminderScheduler {
  /**
   * Запланировать уведомление через [inMinutes] минут с текстом [message].
   */
  fun scheduleReminder(inMinutes: Int, message: String)
}
