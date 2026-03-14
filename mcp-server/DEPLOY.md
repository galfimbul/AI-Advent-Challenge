# Развёртывание MCP-сервера (День 17)

Сервер предоставляет один mock-инструмент `mock_echo` по протоколу MCP (Streamable HTTP / SSE + POST). Настройка приведена в соответствие с [официальным примером сервера](https://github.com/modelcontextprotocol/kotlin-sdk?tab=readme-ov-file#creating-a-server): установлены `ContentNegotiation` с `json(McpJson)` и зависимости для сериализации.

## Транспорты MCP: Stdio vs HTTP

**Stdio (StdioServerTransport)** — обмен сообщениями через **stdin/stdout**: клиент запускает сервер как дочерний процесс и общается с ним по каналу (пайп). JSON-RPC сообщения идут построчно в stdin, ответы — в stdout. Используется в десктопных клиентах (Cursor, Claude Desktop, IDE): клиент сам запускает процесс `mcp-server` и не нужен сетевой порт. Для доступа с телефона или по сети Stdio не подходит.

**Streamable HTTP** (как в этом проекте) — один HTTP-эндпоинт (`/mcp`), POST для запросов, при необходимости SSE для стриминга. Сервер слушает порт (например 8080), к нему можно обращаться по сети с Android-приложения, через туннель или с другого хоста. Для Дня 17 выбран HTTP, чтобы приложение подключалось к серверу по URL.

## Ручная проверка (curl / Postman)

Сервер возвращает **406 Not Acceptable**, если в запросе нет заголовка **Accept** с обоими типами: `application/json` и `text/event-stream`. Обязательно добавь заголовок:

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

В Postman/Insomnia: вкладка Headers → добавь `Accept` = `application/json, text/event-stream`. (Без `charset=UTF-8` — иначе сервер может вернуть 406.)

## Сборка

Из корня проекта:

```bash
./gradlew :mcp-server:installDist
```

Исполняемые файлы появятся в `mcp-server/build/install/mcp-server/bin/`:
- `mcp-server` (Unix)
- `mcp-server.bat` (Windows)

Либо запуск без установки:

```bash
./gradlew :mcp-server:run
```

## Локальная отладка в Android Studio (Mac)

Чтобы ставить брейкпоинты и дебажить сервер без консоли:

1. **Run-конфигурация**  
   В проекте уже есть конфигурация **«MCP Server»** (`.idea/runConfigurations/MCP_Server.xml`). Если её нет — создайте вручную: **Run → Edit Configurations → + → Application**:
   - **Main class:** `com.example.mcpserver.MainKt`
   - **Module:** выберите модуль `mcp-server` (или `mcp-server.main`, как предлагает IDE)
   - **Environment variables:** `PORT=8080` (опционально, по умолчанию порт 8080)

2. **Запуск под отладчиком**  
   Выберите в списке конфигураций **«MCP Server»** и нажмите **Debug** (иконка жука). Сервер поднимется на `http://127.0.0.1:8080`.

3. **Брейкпоинты**  
   Ставьте в `Main.kt`: в `addTool { ... }` (вызов `mock_echo`) или в любом месте обработки.

4. **Проверка с эмулятора**  
   В `secret.properties` задайте:
   ```properties
   MCP_CUSTOM_SERVER_URL=http://10.0.2.2:8080/mcp
   ```
   Эмулятор Android достучится до Mac по `10.0.2.2:8080`. Запустите приложение, нажмите **/mock** — запросы пойдут на локальный сервер, сработают брейкпоинты.

5. **Через туннель на VPS**  
   Если нужно дебажить запросы, идущие через туннель: на Mac выполните `ssh -L 8443:127.0.0.1:8080 user@VPS`, на VPS запустите сервер (или проксируйте на Mac). В приложении укажите `http://10.0.2.2:8443/mcp`. Для отладки самого сервера проще подключать приложение напрямую к Mac (п. 4).

## Что изменить перед запуском на VPS

1. **Приложение (на машине разработки)**  
   В `secret.properties` укажите URL сервера на VPS:
   - по HTTP: `MCP_CUSTOM_SERVER_URL=http://ВАШ_VPS_IP:8080/mcp`  
   - по HTTPS (рекомендуется для Android): `MCP_CUSTOM_SERVER_URL=https://ваш-домен/mcp` (после настройки nginx + Let's Encrypt).

2. **Сервер на VPS**  
   - Код менять не нужно: по умолчанию Ktor слушает на `0.0.0.0` (все интерфейсы), порт задаётся переменной `PORT` (по умолчанию 8080).  
   - Откройте порт в файрволе (8080 при прямом доступе или только 80/443 при прокси через nginx).  
   - Для Android 9+ желательно поднять HTTPS (nginx + Let's Encrypt), иначе cleartext по IP может блокироваться.

3. **Краткий порядок**  
   Собрать `installDist` → скопировать каталог на VPS → установить Java 17 → запустить сервер (или systemd) → в приложении прописать URL.

---

## Запуск на VPS (Ubuntu 20.04)

Сервер — JVM-приложение, нужна Java 17.

### 1. Установка Java 17 на Ubuntu 20.04

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version   # должно показать 17.x
```

Либо только JRE (без компилятора): `openjdk-17-jre-headless`.

### 2. Копирование файлов на VPS

На своей машине (из корня проекта) соберите дистрибутив и скопируйте его на VPS:

```bash
./gradlew :mcp-server:installDist
scp -r mcp-server/build/install/mcp-server USER@ВАШ_VPS_IP:~/
```

Замените `USER` на имя пользователя на VPS, `ВАШ_VPS_IP` — на IP или домен.

### 3. Запуск на VPS

Подключитесь по SSH и запустите:

```bash
ssh USER@ВАШ_VPS_IP
cd ~/mcp-server
PORT=8080 ./bin/mcp-server
```

Сервер будет слушать порт 8080. Для выхода нажмите Ctrl+C.

### 4. Запуск в фоне (systemd, опционально)

Чтобы сервер работал после отключения SSH и перезапускался при сбоях:

```bash
sudo nano /etc/systemd/system/mcp-server.service
```

Содержимое (подставьте свой путь и пользователя):

```ini
[Unit]
Description=MCP Mock Server (Day 17)
After=network.target

[Service]
Type=simple
User=YOUR_USER
WorkingDirectory=/home/YOUR_USER/mcp-server
Environment="PORT=8080"
ExecStart=/home/YOUR_USER/mcp-server/bin/mcp-server
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Включить и запустить:

```bash
sudo systemctl daemon-reload
sudo systemctl enable mcp-server
sudo systemctl start mcp-server
sudo systemctl status mcp-server
```

Логи: `journalctl -u mcp-server -f`.

### 5. Файрвол

Если включён UFW, откройте порт 8080:

```bash
sudo ufw allow 8080/tcp
sudo ufw reload
```

### 6. URL в приложении

В `secret.properties` на машине разработки укажите:

`MCP_CUSTOM_SERVER_URL=http://ВАШ_VPS_IP:8080/mcp`

**Android 9+ по умолчанию запрещает cleartext (HTTP)** к внешним хостам. Чтобы работало по HTTPS, настройте nginx с SSL (см. ниже).

---

## HTTPS через nginx и Let's Encrypt

Чтобы приложение подключалось по `https://` (и обходило ограничение cleartext), поставьте перед MCP-сервером nginx с TLS. Сертификат Let's Encrypt выдаётся только для **доменного имени**, не для голого IP — нужен домен, указывающий на ваш VPS (например, поддомен типа `mcp.ваш-домен.ru` или бесплатный DuckDNS).

### 1. Домен

Убедитесь, что у вас есть домен (или поддомен) и A-запись указывает на IP VPS. Пример: `mcp.example.com` → IP вашего сервера.

### 2. Установка nginx и Certbot на VPS (Ubuntu 20.04)

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

### 3. Временный конфиг nginx (для получения сертификата)

Создайте конфиг (подставьте свой домен):

```bash
sudo nano /etc/nginx/sites-available/mcp-server
```

Содержимое (замените `mcp.example.com` на ваш домен):

```nginx
server {
    listen 80;
    server_name mcp.example.com;
    location / {
        return 200 'ok';
        add_header Content-Type text/plain;
    }
}
```

Включите сайт и получите сертификат:

```bash
sudo ln -sf /etc/nginx/sites-available/mcp-server /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d mcp.example.com
```

Certbot спросит email и согласие с условиями; при необходимости введёте пароль. Он сам поправит конфиг nginx на HTTPS.

### 4. Проксирование /mcp на MCP-сервер

Отредактируйте конфиг, чтобы запросы к `/mcp` шли на локальный порт 8080:

```bash
sudo nano /etc/nginx/sites-available/mcp-server
```

Замените содержимое на (подставьте свой домен и путь к сертификатам). **Заголовки ответа бэкенда не переопределяем** — иначе клиент падает с «Streamable HTTP error». Не добавляйте свой `Content-Type` для GET (бэкенд уже отдаёт `text/event-stream`; дублирование ломает клиент).

```nginx
server {
    listen 80;
    server_name mcp.example.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name mcp.example.com;

    ssl_certificate     /etc/letsencrypt/live/mcp.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mcp.example.com/privkey.pem;

    location /mcp {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Accept "application/json, text/event-stream";
        # Если 406 сохраняется, попробуйте без пробела: "application/json,text/event-stream"
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection '';

        proxy_buffering off;
        proxy_request_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;

        gzip off;
    }
}
```

Проверьте конфиг и перезагрузите nginx:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

**Рабочий пример целиком** (DuckDNS или свой домен + Let's Encrypt):

```nginx
map $request_method $mcp_content_type {
    GET     "text/event-stream";
    default "application/json";
}
server {
    if ($host = mcp.example.com) {
        return 301 https://$host$request_uri;
    }
    listen 80;
    server_name mcp.example.com;
    return 404;
}

server {
    listen 443 ssl;
    server_name mcp.example.com;

    ssl_certificate     /etc/letsencrypt/live/mcp.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mcp.example.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location / {
        return 200 'ok';
        add_header Content-Type text/plain;
    }

    location /mcp {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Accept "application/json, text/event-stream";
        proxy_set_header Connection "";

        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding on;
        tcp_nopush on;
        tcp_nodelay on;
        keepalive_timeout 65;
    }
}
```

Замените `mcp.example.com` на свой домен и при необходимости пути к сертификатам (например после `certbot --nginx -d ваш-домен`).

### 5. Файрвол

Откройте 80 и 443, порт 8080 наружу можно не открывать (доступ только через nginx):

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 22/tcp
# sudo ufw allow 8080/tcp   # не нужен, если только через nginx
sudo ufw reload
```

### 6. URL в приложении при HTTPS

В `secret.properties` укажите:

`MCP_CUSTOM_SERVER_URL=https://mcp.example.com/mcp`

(замените `mcp.example.com` на ваш домен)

### 7. Продление сертификата

Let's Encrypt выдаёт сертификаты на 90 дней. Продление:

```bash
sudo certbot renew --dry-run   # проверка
sudo certbot renew             # продление (или настройте cron)
```

---

## Только IP, без домена

Если домена нет, Let's Encrypt не подойдёт. Варианты:

- **Бесплатный домен:** завести поддомен на [DuckDNS](https://www.duckdns.org/) или аналоге, указать его на IP VPS — дальше по инструкции выше с nginx + certbot.
- **Самостоятельный TLS в Ktor:** можно включить в приложении TLS с самоподписанным сертификатом и в Android добавить Network Security Config, доверяющий этому сертификату — сложнее в поддержке и менее безопасно.

---

## Тест без nginx (прямой доступ на порт 8080)

Чтобы проверить, что 406 или Streamable HTTP error связаны с nginx, можно временно обращаться к MCP-серверу напрямую.

**1. На VPS — открыть порт 8080 в файрволе:**
```bash
sudo ufw allow 8080/tcp
sudo ufw reload
```

**2. В приложении — разрешить HTTP (cleartext) для хоста VPS:**  
В `app/src/main/res/xml/network_security_config.xml` в списке `<domain>` должен быть твой хост (например `mcp.example.com`) или IP VPS.

**3. В `secret.properties` указать URL без HTTPS:**
```properties
MCP_CUSTOM_SERVER_URL=http://mcp.example.com:8080/mcp
```
(подставь свой домен или `http://ВАШ_IP:8080/mcp`)

**4. Пересобрать приложение и проверить /mock.**  
Если без nginx всё работает — причина была в прокси. После теста можно вернуть HTTPS-URL и при необходимости снова закрыть 8080: `sudo ufw delete allow 8080/tcp && sudo ufw reload`.

---

## Запуск на VPS (общее)

1. Скопируйте на сервер каталог `mcp-server/build/install/mcp-server/` (или собранный JAR, если настроена задача shadowJar).
2. На VPS запустите (нужна Java 17):
   ```bash
   PORT=8080 ./bin/mcp-server
   ```
   По умолчанию порт 8080; переменная окружения `PORT` переопределяет его.
3. Убедитесь, что порт открыт в файрволе. При необходимости настройте reverse proxy (nginx) для HTTPS и укажите в приложении URL вида `https://your-domain/mcp` (если прокси проксирует путь `/mcp` на этот сервис).

## URL для приложения

Клиент подключается по Streamable HTTP к пути `/mcp`. Полный URL:
- Локально: `http://localhost:8080/mcp`
- На VPS по HTTPS (рекомендуется для Android): `https://ваш-домен/mcp` — настройте nginx + Let's Encrypt (см. раздел выше).
- На VPS по HTTP: `http://ВАШ_IP:8080/mcp` — на Android 9+ по умолчанию заблокировано (cleartext).

В `secret.properties` укажите, например: `MCP_CUSTOM_SERVER_URL=https://mcp.example.com/mcp`

---

## Ошибка «Streamable HTTP error» на VPS (приложение подключается к nginx)

Если локально всё работает, а через VPS приложение падает с `StreamableHttpError: Streamable HTTP error:` (часто с пустым сообщением после двоеточия), причина обычно в ответе через nginx или в таймауте.

**Что проверить:**

1. **Явно передать Accept в nginx**  
   В `location /mcp` добавьте строку:
   `proxy_set_header Accept "application/json, text/event-stream";`
   Тогда бэкенд всегда получит этот заголовок, даже если прокси или клиент его как-то меняют. После правки: `sudo nginx -t && sudo systemctl reload nginx`.

2. **Проверка с самого VPS (без nginx)**  
   По SSH на VPS выполните:
   ```bash
   curl -v -X POST "http://127.0.0.1:8080/mcp" \
     -H "Accept: application/json, text/event-stream" \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
   ```
   Если здесь **200**, а через домен — 406, значит заголовок по пути через nginx искажается; помогает п. 1. Если и на 127.0.0.1 приходит **406**, попробуйте в nginx вариант без пробела: `proxy_set_header Accept "application/json,text/event-stream";`

3. **Не трогать заголовки ответа в nginx**  
   В `location /mcp` не должно быть `add_header Content-Type ...` для GET — бэкенд сам отдаёт `Content-Type: text/event-stream`. Дублирование или подмена ломает клиент.

4. **Host**  
   Используйте `proxy_set_header Host $host;` (как в конфиге выше), а не `Host 127.0.0.1:8080`.

5. **Проверка с хоста до nginx**  
   См. блок «Полный curl для проверки с хоста» ниже. Ожидается ответ 200 и JSON с `result`. Если видите 502/504 или не-JSON — смотрите логи nginx и что слушает порт 8080 на VPS.

6. **Логи на VPS**  
   - Ошибки nginx: `sudo tail -f /var/log/nginx/error.log`
   - Работает ли MCP-сервер: `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/mcp` (на VPS) — не обязан быть 200 на GET, но сервер должен отвечать.

7. **Без nginx (для проверки)**  
   Временно откройте порт 8080 в файрволе и в приложении укажите `http://ВАШ_VPS_IP:8080/mcp`. Если так заработает — проблема в конфиге или работе nginx.

8. **Логи в приложении**  
   В коде добавлено логирование причин (тег `McpCustomClient`). В Logcat смотрите строки после «Failed to connect» / «Caused by» — иногда там есть код ответа или тело.

### Полный curl для проверки с хоста

Подставьте свой URL (локально `http://localhost:8080/mcp` или на VPS `https://ваш-домен/mcp`) и выполните на своей машине.

**Шаг 1 — initialize (обязательно, проверяет доступность сервера):**

```bash
curl -v -X POST "https://ваш-домен/mcp" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

Ожидается: HTTP 200, в теле JSON с полем `result` (например `result.serverInfo`).

**Шаг 2 — вызов mock_echo (опционально):**  
Сначала выполните шаг 1 и сохраните заголовок `mcp-session-id` из ответа (или `Mcp-Session-Id`). Подставьте его в `SESSION_ID` и снова свой URL в `URL`:

```bash
URL="https://ваш-домен/mcp"
SESSION_ID="значение из заголовка mcp-session-id ответа шага 1"

# Уведомление initialized (нужно после initialize)
curl -s -X POST "$URL" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# Вызов инструмента mock_echo
curl -s -X POST "$URL" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"mock_echo","arguments":{"message":"привет"}}}'
```

В ответе на последний запрос ожидается JSON с `result.content[].text` вида `Echo: привет | Время: <ISO-8601>`.

## Инструмент mock_echo

- **Вход:** параметр `message` (строка).
- **Выход:** текст вида `Echo: <message> | Время: <ISO-8601>`.
