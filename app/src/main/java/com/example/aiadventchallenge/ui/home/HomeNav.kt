package com.example.aiadventchallenge.ui.home

data class HomeNavItem(
  val label: String,
  val route: String
)

data class HomeSection(
  val id: String,
  val title: String,
  val items: List<HomeNavItem>
)

val HOME_SECTIONS: List<HomeSection> = listOf(
  HomeSection(
    id = "prompting",
    title = "Промптинг",
    items = listOf(
      HomeNavItem("Чат", "chat"),
      HomeNavItem("Обсуждение", "discussion"),
      HomeNavItem("Температура", "temperature"),
      HomeNavItem("Версии моделей", "modelcomparison")
    )
  ),
  HomeSection(
    id = "agent",
    title = "Агент",
    items = listOf(
      HomeNavItem("Начать диалог", "agent")
    )
  )
)
