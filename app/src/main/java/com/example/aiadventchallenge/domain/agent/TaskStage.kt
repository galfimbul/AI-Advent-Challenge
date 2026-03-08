package com.example.aiadventchallenge.domain.agent

/**
 * Этап задачи в конечном автомате (День 13).
 * Порядок: Planning → Execution → Validation → Done.
 */
enum class TaskStage {
  Planning,
  Execution,
  Validation,
  Done;

  fun displayName(): String = when (this) {
    Planning -> "Планирование"
    Execution -> "Выполнение"
    Validation -> "Проверка"
    Done -> "Готово"
  }
}

/** Текст ожидаемого действия для промпта агента (единый источник в коде). */
fun TaskStage.expectedActionText(): String = when (this) {
  TaskStage.Planning -> "предложи план"
  TaskStage.Execution -> "выполняй шаги"
  TaskStage.Validation -> "Проверь результат. Если выполнение корректно — предложи пользователю подтвердить корректность (/confirm). Иначе укажи, что переделать, и предложи вернуться к этапу выполнения (/reject)."
  TaskStage.Done -> "подведи итог"
}

fun TaskStage.asString(): String = when (this) {
  TaskStage.Planning -> "planning"
  TaskStage.Execution -> "execution"
  TaskStage.Validation -> "validation"
  TaskStage.Done -> "done"
}

fun taskStageFromString(s: String?): TaskStage = when (s) {
  "execution" -> TaskStage.Execution
  "validation" -> TaskStage.Validation
  "done" -> TaskStage.Done
  else -> TaskStage.Planning
}

/** Состояние задачи: этап, шаг, пауза. Ожидаемое действие вычисляется по этапу. */
data class TaskState(
  val stage: TaskStage,
  val currentStep: Int,
  val isPaused: Boolean
)
