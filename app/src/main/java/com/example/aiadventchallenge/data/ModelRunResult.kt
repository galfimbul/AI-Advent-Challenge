package com.example.aiadventchallenge.data

/**
 * Результат одного запроса к одной модели для экрана «Версии моделей».
 * @param modelId идентификатор модели для API (например gpt-4o-mini)
 * @param displayName короткое имя для UI
 * @param content текст ответа
 * @param promptTokens токены запроса
 * @param completionTokens токены ответа
 * @param totalTokens всего токенов
 * @param responseTimeMs время ответа в миллисекундах
 * @param costUsd ориентировочная стоимость в USD (актуальные цены: https://openai.com/api/pricing)
 */
data class ModelRunResult(
  val modelId: String,
  val displayName: String,
  val content: String,
  val promptTokens: Int?,
  val completionTokens: Int?,
  val totalTokens: Int?,
  val responseTimeMs: Long,
  val costUsd: Double?
)
