# AI Advent Challenge

Android приложение для взаимодействия с ChatGPT через OpenAI API.

## Описание

Приложение позволяет отправлять текстовые запросы к ChatGPT и получать ответы в реальном времени. Реализовано с использованием Jetpack Compose для современного UI и Retrofit для работы с API.

### Функционал

- ✅ Отправка текстовых запросов к ChatGPT
- ✅ Отображение ответов от AI в удобном интерфейсе
- ✅ Безопасное хранение API ключа (не коммитится в репозиторий)
- ✅ Логирование всех запросов и ответов в Logcat
- ✅ Таймаут запросов 60 секунд для длинных ответов
- ✅ Обработка ошибок с понятными сообщениями
- ✅ Установка максимального количества токенов на ответ модели
- ✅ Остановка генерации ответа моделью при stop sequence "Закончил ответ"
- ✅ Игнорирование ограничений ответа модели

## Требования

- Android Studio Hedgehog или новее
- JDK 11 или выше
- Android SDK (minSdk 26, targetSdk 36)
- API ключ OpenAI (получить можно на [platform.openai.com/api-keys](https://platform.openai.com/api-keys))

## Установка и настройка

### 1. Клонирование репозитория

```bash
git clone <repository-url>
cd AIAdventChallenge
```

### 2. Настройка API ключа

1. Скопируйте файл `secret.properties.example` в `secret.properties`:
   ```bash
   cp secret.properties.example secret.properties
   ```

2. Откройте `secret.properties` и замените `ваш ключ` на ваш реальный API ключ OpenAI:
   ```
   OPENAI_API_KEY=sk-your-actual-api-key-here
   ```

   ⚠️ **Важно:** Файл `secret.properties` добавлен в `.gitignore` и не будет закоммичен в репозиторий. Никогда не публикуйте свой API ключ!

### 3. Сборка проекта

#### Через Android Studio:

1. Откройте проект в Android Studio
2. Дождитесь синхронизации Gradle
3. Нажмите `Build > Make Project` или `Ctrl+F9` (Windows/Linux) / `Cmd+F9` (Mac)
4. Для запуска на эмуляторе или устройстве: `Run > Run 'app'` или `Shift+F10` (Windows/Linux) / `Ctrl+R` (Mac)

#### Через командную строку:

```bash
# Сборка debug APK
./gradlew assembleDebug

# Установка на подключенное устройство
./gradlew installDebug

# Запуск приложения
adb shell am start -n com.example.aiadventchallenge/.MainActivity
```

## Использование

1. Запустите приложение на устройстве или эмуляторе
2. В поле "Ваш запрос" введите вопрос или сообщение для ChatGPT
3. Нажмите кнопку "Отправить"
4. Ответ от ChatGPT появится в поле "Ответ"

### Просмотр логов

Все HTTP запросы и ответы логируются в Logcat с тегом `OpenAI`. Для просмотра:

1. В Android Studio откройте вкладку **Logcat**
2. В фильтре введите `OpenAI` или выберите тег `OpenAI`
3. Вы увидите полные запросы и ответы API

## Структура проекта

```
app/src/main/java/com/example/aiadventchallenge/
├── data/
│   ├── ChatRepository.kt          # Репозиторий для работы с API
│   └── openai/
│       ├── OpenAiApi.kt           # Retrofit интерфейс для OpenAI API
│       └── OpenAiDto.kt           # Data классы для запросов/ответов
└── ui/
    └── chat/
        ├── ChatScreen.kt          # Compose UI экран чата
        └── ChatViewModel.kt       # ViewModel для управления состоянием
```

## Технологии

- **Kotlin** - основной язык разработки
- **Jetpack Compose** - современный UI фреймворк
- **Retrofit** - HTTP клиент для работы с API
- **OkHttp** - HTTP клиент с логированием
- **Coroutines** - асинхронное программирование
- **ViewModel** - управление UI состоянием
- **Material 3** - дизайн система

## Модель AI

По умолчанию используется модель **gpt-5**. Модель можно изменить в файле `app/src/main/java/com/example/aiadventchallenge/data/openai/OpenAiDto.kt`.

## Устранение неполадок

### Ошибка "OPENAI_API_KEY не задан"

Убедитесь, что:
- Файл `secret.properties` существует в корне проекта
- В файле указан корректный ключ (не "ваш ключ")
- После изменения `secret.properties` выполните `Build > Clean Project` и `Build > Rebuild Project`

### Ошибки сборки

1. Выполните `File > Invalidate Caches / Restart` в Android Studio
2. Удалите папки `.gradle` и `build` в корне проекта
3. Выполните `./gradlew clean` в терминале
4. Пересоберите проект

### Проблемы с сетью

- Проверьте подключение к интернету
- Убедитесь, что устройство/эмулятор имеет доступ в сеть
- Проверьте логи в Logcat для деталей ошибки

## Лицензия

Этот проект создан в рамках AI Advent Challenge.
