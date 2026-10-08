# tg_claude_connector

Шаблон коннектора, который читает Telegram-каналы и чаты ботов.
Построен на **DDD** и **Clean Architecture**: Java 25 (LTS), Spring Boot 4.1, Maven.

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
        │   adapter.in.telegram     Bot API long polling → ReceiveBotMessageUseCase
        │   adapter.in.web          REST API → use cases
        │   adapter.in.scheduling   @Scheduled → PollChannelsUseCase
        │   adapter.out.telegram    ChannelFeedPort ← t.me/s/<channel> (jsoup)
        │   adapter.out.persistence SourceRepository / MessageRepository (in-memory)
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

Нужен JDK 25.

```bash
./mvnw verify                                    # сборка и тесты
./mvnw -pl connector-bootstrap -am spring-boot:run

# с ботом
TELEGRAM_BOT_ENABLED=true TELEGRAM_BOT_TOKEN=123:abc ./mvnw -pl connector-bootstrap -am spring-boot:run

# Docker
docker build -t tg-connector . && docker run -p 8080:8080 -e TELEGRAM_BOT_ENABLED=true -e TELEGRAM_BOT_TOKEN=... tg-connector
```

Настройки — в `connector-bootstrap/src/main/resources/application.yml` (префикс `connector.telegram`):

| Свойство | По умолчанию | Описание |
|---|---|---|
| `bot.enabled` / `TELEGRAM_BOT_ENABLED` | `false` | включить Bot API |
| `bot.token` / `TELEGRAM_BOT_TOKEN` | — | токен бота |
| `bot.auto-register-chats` / `TELEGRAM_BOT_AUTO_REGISTER_CHATS` | `false` | автоматически регистрировать чаты, из которых пришло сообщение. По умолчанию выключено: написать боту или добавить его в группу может кто угодно, поэтому чаты лучше регистрировать через API |
| `channel-feed.base-url` | `https://t.me` | адрес веб-превью |
| `polling.enabled` / `polling.interval` | `true` / `60s` | расписание опроса каналов |

Чтобы бот получал посты канала, добавьте его администратором канала. Чтобы видел все сообщения
в группе, отключите privacy mode в @BotFather (`/setprivacy`).

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

## Как расширять

- **Постоянное хранилище**: реализовать `SourceRepository` и `MessageRepository` (JPA/JDBC)
  в `adapter.out.persistence` и убрать in-memory реализации.
- **Доставка сообщений дальше** (Kafka, webhook, LLM-обработка): свой `DomainEventPublisher`
  или `@EventListener` на `MessageReceived`.
- **MTProto** (приватные каналы, история): своя реализация `ChannelFeedPort`.
- **Редактирования/удаления сообщений**: новый use case и доменные события
  (`MessageEdited`, ...); сейчас `TelegramUpdateMapper` пропускает `edited_*` апдейты.

In-memory хранилище и in-process события годятся для шаблона, но не переживают рестарт и
не рассчитаны на несколько инстансов. Изменения источников и сообщений внутри одного процесса
сериализуются в `IngestionService`. Для БД понадобятся транзакции, уникальный индекс по
`reference` (контракт `SourceRepository#save`) и optimistic locking на `Source`.

REST API пока без аутентификации — перед выкладкой наружу добавьте Spring Security.
