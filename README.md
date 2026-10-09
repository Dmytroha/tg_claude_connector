# tg_claude_connector

[![CI](https://github.com/Dmytroha/tg_claude_connector/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/Dmytroha/tg_claude_connector/actions/workflows/ci.yml)

Коннектор, который читает Telegram-каналы и чаты ботов, хранит сообщения в PostgreSQL и даёт
Claude доступ к ним через **MCP** (custom connector в claude.ai и приложении Claude).
Построен на **DDD** и **Clean Architecture**: Java 25 (LTS), Spring Boot 4.1, Spring AI 2.0, Maven.

> ⚠️ MCP-endpoint и REST API пока **без аутентификации**. Не выставляйте коннектор в интернет,
> пока не добавлен OAuth (следующий шаг).

## Источники данных

| Тип источника | Как подключается | Режим | Адаптер |
|---|---|---|---|
| Публичный канал (`@name`, `https://t.me/name`) | без авторизации, через веб-превью `https://t.me/s/<name>` | `POLLING` (pull по расписанию) | `WebPreviewChannelFeedAdapter` |
| Любой чат, где есть бот: личка, группа, канал, в котором бот — админ (числовой `chat_id`) | токен от @BotFather, Bot API long polling | `BOT_UPDATES` (push) | `TelegramBotUpdateListener` |

Приватные каналы и полную историю можно добавить через MTProto-клиент (TDLib/TDLight):
достаточно реализовать порт `ChannelFeedPort` — домен и use case'ы менять не нужно.

## Архитектура

```
connector-bootstrap       Spring Boot main, composition root (wiring use cases), ArchUnit-тесты
        │
connector-infrastructure  Адаптеры
        │   adapter.in.mcp          MCP-инструменты для Claude (Streamable HTTP, /mcp) → use cases
        │   adapter.in.telegram     Bot API long polling → ReceiveBotMessageUseCase
        │   adapter.in.web          REST API → use cases
        │   adapter.in.scheduling   @Scheduled → PollChannelsUseCase
        │   adapter.out.telegram    ChannelFeedPort ← t.me/s/<channel> (jsoup)
        │   adapter.out.persistence SourceRepository / MessageRepository: postgres (JDBC + Flyway) или memory
        │   adapter.out.events      DomainEventPublisher → Spring events
        │
connector-application     Use cases (port.in), порты (port.out), сервисы. Без Spring
        │
connector-domain          Агрегаты, value objects, доменные события, интерфейсы репозиториев. Без фреймворков
```

Зависимости направлены только внутрь. Правило проверяется `ArchitectureTest` (ArchUnit) и
разделением на Maven-модули: в `domain` и `application` нет Spring, Telegram SDK, Jackson и т.п.

### Доменная модель

- **`Source`** (агрегат): чат, из которого читаем. `ChatReference` — sealed-тип
  (`ChannelUsername` | `ChatId`), от него зависит `IngestionMode`. Хранит курсор
  `lastReadMessageId`, статус `ACTIVE`/`PAUSED` и гарантирует, что каждое сообщение принимается
  ровно один раз и только пока источник активен (`Source#accept` — фабрика `Message`).
- **`Message`** (агрегат): неизменяемое сообщение; ключ `MessageKey(sourceId, telegramMessageId)`,
  контент — текст и вложения.
- **События**: `SourceRegistered`, `SourcePaused`, `SourceResumed`, `MessageReceived`.
  `MessageReceived` — основная точка интеграции с внешними системами
  (см. `LoggingDomainEventListener`).

Адаптеры Telegram — anti-corruption layer: `TelegramUpdateMapper` и `WebPreviewHtmlParser`
переводят объекты Telegram в доменный язык (`IncomingMessage`).

## Запуск

```bash
# приложение + PostgreSQL (нужен только Docker)
cp .env.example .env          # задайте DB_PASSWORD, при необходимости токен бота
docker compose up -d --build

# без базы, данные в памяти (нужен JDK 25)
./mvnw -pl connector-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=memory

./mvnw verify                 # сборка и тесты; тесты PostgreSQL идут в Testcontainers, если есть Docker
```

Хранилище выбирается свойством `connector.persistence`: `postgres` (по умолчанию) или `memory`
(профиль `memory`, данные теряются при перезапуске). Схему БД создаёт Flyway при старте
(`connector-infrastructure/src/main/resources/db/migration`).

Настройки — в `connector-bootstrap/src/main/resources/application.yml`:

| Свойство | По умолчанию | Описание |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/tgconnector` / `tgconnector` / — | подключение к PostgreSQL |
| `connector.telegram.bot.enabled` / `TELEGRAM_BOT_ENABLED` | `false` | включить Bot API |
| `connector.telegram.bot.token` / `TELEGRAM_BOT_TOKEN` | — | токен бота |
| `connector.telegram.bot.auto-register-chats` / `TELEGRAM_BOT_AUTO_REGISTER_CHATS` | `false` | автоматически регистрировать чаты, из которых пришло сообщение. По умолчанию выключено: написать боту или добавить его в группу может кто угодно, поэтому чаты лучше регистрировать через API |
| `connector.telegram.channel-feed.base-url` | `https://t.me` | адрес веб-превью |
| `connector.telegram.polling.enabled` / `.interval` | `true` / `60s` | расписание опроса каналов |

Чтобы бот получал посты канала, добавьте его администратором канала. Чтобы видел все сообщения
в группе, отключите privacy mode в @BotFather (`/setprivacy`).

## Секреты

Приложение читает секреты **только из переменных окружения**, в коде и в `application.yml`
их нет. Откуда берутся переменные, зависит от среды:

| Среда | Где хранятся секреты |
|---|---|
| Локально | файл `.env` (в `.gitignore`); шаблон с описанием переменных — `.env.example` |
| GitHub Actions | Settings → Secrets and variables → Actions (сейчас тестам секреты не нужны) |
| Продакшен | хранилище секретов хостинга (Variables/Secrets в панели) или файл с правами `600` вне репозитория на VPS |

Каждый пуш и PR проверяет [gitleaks](https://github.com/gitleaks/gitleaks) (job «Secret scan» в CI):
если в истории коммитов найдётся токен или ключ, сборка упадёт. Если секрет всё-таки попал
в репозиторий, удаления коммита недостаточно — секрет нужно сразу перевыпустить
(для бота: @BotFather → `/revoke`).

## MCP: подключение к Claude

Коннектор — удалённый MCP-сервер (Streamable HTTP) по адресу `https://<ваш-домен>/mcp`.
Его добавляют в claude.ai: **Settings → Connectors → Add custom connector**, после чего он
доступен и в приложении Claude на телефоне. Для этого коннектор должен быть доступен из интернета
по HTTPS и защищён аутентификацией (OAuth — следующий шаг).

| Инструмент | Что делает |
|---|---|
| `list_sources` | список каналов и чатов, их статус |
| `get_recent_messages` | последние сообщения источника |
| `search_messages` | поиск по тексту (подстрока, без учёта регистра) по всем или выбранным источникам, с фильтром по дате |
| `add_source` | начать читать канал (`@name`, ссылка) или чат бота (`chat_id`) |
| `pause_source` / `resume_source` | приостановить / возобновить источник |
| `refresh_sources` | опросить каналы сейчас, не дожидаясь расписания |

Ответы содержат ссылки на посты (`https://t.me/<канал>/<id>`), чтобы Claude мог на них ссылаться.
Сообщения собираются с момента добавления источника — старой истории канала нет.

Проверить вручную:

```bash
curl -i localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

## REST API

```bash
# зарегистрировать публичный канал (или числовой chat_id для бота)
curl -XPOST localhost:8080/api/v1/sources -H 'Content-Type: application/json' \
     -d '{"reference":"https://t.me/telegram", "title":"Telegram News"}'

curl localhost:8080/api/v1/sources                          # список источников
curl localhost:8080/api/v1/sources/{id}/messages?limit=20   # последние сообщения
curl -XPOST localhost:8080/api/v1/sources/{id}/pause        # пауза / resume
curl -XPOST localhost:8080/api/v1/sources/poll              # опросить каналы сейчас
```

Ошибки возвращаются в формате RFC 9457 Problem Details (404 / 409 / 422).

## Хранилище и конкурентность

- `sources.reference` уникален: один чат нельзя зарегистрировать дважды.
- У `Source` есть версия (optimistic locking): если источник изменили параллельно, запись
  отклоняется (`ConcurrentSourceModificationException`, в REST — 409), а опрос повторится в следующий раз.
- Сохранение сообщения идемпотентно (`ON CONFLICT DO NOTHING`), поэтому повторный опрос не создаёт дублей.
- Поиск — `ILIKE` с trigram-индексом (`pg_trgm`), работает для любых языков.
- Один инстанс приложения: планировщик опроса не координируется между несколькими инстансами.

## Как расширять

- **Доставка сообщений дальше** (Kafka, webhook, LLM-обработка): свой `DomainEventPublisher`
  или `@EventListener` на `MessageReceived`.
- **MTProto** (приватные каналы, история): своя реализация `ChannelFeedPort`.
- **Редактирования/удаления сообщений**: новый use case и доменные события
  (`MessageEdited`, ...); сейчас `TelegramUpdateMapper` пропускает `edited_*` апдейты.
- **Семантический поиск**: эмбеддинги в PostgreSQL (`pgvector`) вместо подстроки.
