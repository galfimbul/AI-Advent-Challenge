package com.example.aiadventchallenge.domain

/** Диапазон температуры по документации OpenAI: 0–2, шаг 0.1. */
const val MIN_TEMP = 0f
const val MAX_TEMP = 2f
const val TEMP_STEP = 0.1f

/** Пресеты для быстрых кнопок: значение и подпись «Название (значение)». */
val TEMPERATURE_PRESETS: List<Pair<Float, String>> = listOf(
  0f to "Точность (0)",
  0.7f to "Баланс (0.7)",
  1.2f to "Креативность (1.2)"
)

fun labelForTemperature(temp: Float): String =
  TEMPERATURE_PRESETS.find { it.first == temp }?.second ?: "Температура (${"%.1f".format(temp)})"
