# Модуль `doc-index` — локальная индексация документов (День 21)

JVM-приложение на Kotlin: нарезка корпуса репозитория двумя стратегиями, эмбеддинги через **Ollama**, запись в **SQLite** (`doc_index.sqlite`).

## Требования

- **JDK 17** (как у модуля `mcp-server`; Gradle подтянет toolchain при необходимости).
- **[Ollama](https://ollama.com)** установлен и запущен на машине, где выполняется сборка.
- Модель эмбеддингов (один раз):

```bash
ollama pull nomic-embed-text
```

Проверка:

```bash
ollama list
```

## Запуск сервиса Ollama

- **macOS / Linux / Windows:** после установки Ollama обычно работает фоновый сервис.
- API по умолчанию: **`http://127.0.0.1:11434`**.
- Если порт или хост другие, задайте базовый URL:
  - переменная окружения **`OLLAMA_HOST`** (например `http://192.168.1.10:11434` или `192.168.1.10:11434`),  
  - или флаг индексатора **`--ollama-base https://host:port`** (без завершающего `/`).

## Проверка API эмбеддингов

```bash
curl -s http://127.0.0.1:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' | head -c 200
```

В ответе должно быть поле `embedding` (массив чисел).

## Gradle-задачи

| Задача | Назначение |
|--------|------------|
| `:doc-index:buildDocIndex` | Собрать `doc-index/build/doc_index.sqlite` (нужен запущенный Ollama). |
| `:doc-index:printDocIndexReport` | Сначала `buildDocIndex`, затем сводка в консоль и файл `doc-index/build/reports/doc_index_report.md`. |
| `:doc-index:run` | То же, что индексатор с аргументами по умолчанию (корень репозитория и путь вывода как у `buildDocIndex`). |

Примеры:

```bash
./gradlew :doc-index:buildDocIndex
./gradlew :doc-index:printDocIndexReport
```

## Связка с приложением `:app`

Задача **`prepareDocIndexAssets`** выполняется **перед** задачами **`merge*Assets`** (они от неё зависят), сама зависит от `:doc-index:buildDocIndex` и копирует `doc_index.sqlite` в `app/build/generated/docIndexAssets/`, откуда файл попадает в **assets** APK. Полная сборка приложения **требует доступного Ollama** на машине разработчика.

```bash
./gradlew :app:assembleDebug
```

## Корпус и стратегии chunking

- Markdown: все `*.md` в **корне репозитория** и все `*.md` в **`docs/`**.
- Kotlin: все `*.kt` под **`app/src/main/java/`**.

Стратегии:

1. **FIXED_WINDOW** — окно **1200** символов (UTF-16 code units), перекрытие **200**, поле `section`: `part:i/n`.
2. **STRUCTURE** — Markdown по заголовкам `#`…`###`; преамбула до первого заголовка — `(preamble)`; для `.kt` — логически по файлу; фрагменты длиннее **4000** символов дополнительно режутся окнами 1200/200.

Перед запросом к Ollama каждый чанк дополнительно режется до **2048** UTF-16 символов (перекрытие **256**), если длиннее: лимит задаётся в `ModelConstants.MAX_EMBEDDING_INPUT_CHARS` (у модели есть ограничение по **токенам**; код/кириллица «дороже» по токенам). Длинные логические чанки попадают в БД как несколько строк с суффиксом id `__emb_0`, `__emb_1` и пометкой в `section` вида `· embed 1/N`.

## Типичные ошибки

- **Connection refused** — Ollama не запущен или неверный `OLLAMA_HOST`.
- **HTTP 404 / model not found** — не выполнен `ollama pull nomic-embed-text`.
- **HTTP 500, `the input length exceeds the context length`** — редкий случай при очень «тяжёлом» куске текста; уменьшите `MAX_EMBEDDING_INPUT_CHARS` в `ModelConstants.kt` и пересоберите индекс.
- **Таймаут** — первая загрузка модели или перегрузка CPU; повторите запуск.

## Тесты модуля

```bash
./gradlew :doc-index:test
```

(без Ollama — только chunking и утилиты.)
