package com.example.aiadventchallenge.domain.agent

/**
 * Стратегия управления контекстом агента (День 10).
 */
enum class ContextStrategy {
  /** Только последние N сообщений в промпте. */
  SlidingWindow,
  /** Блок facts (ключ-значение) + последние N сообщений; facts обновляются через LLM до ответа. */
  StickyFacts,
  /** Две ветки от одного checkpoint; в промпт — все сообщения текущей ветки. */
  Branching,
  /** Summaries блоков по 10 + последние N (режим Дня 9). */
  Summary
}

fun ContextStrategy.displayName(): String = when (this) {
  ContextStrategy.SlidingWindow -> "Скользящее окно"
  ContextStrategy.StickyFacts -> "Факты"
  ContextStrategy.Branching -> "Ветки"
  ContextStrategy.Summary -> "Сжатие"
}
