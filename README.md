# Max Bot (Java)

Бот для мессенджера Max по ТЗ (юрцентр «Де-факто»).

## Переменные окружения

Обязательные:
- `MAX_ACCESS_TOKEN` — токен бота Max.
- `OPERATOR_USER_ID` — user_id аккаунта юриста в Max.

Опциональные:
- `MAX_API_BASE` — базовый URL API (по умолчанию `https://platform-api.max.ru`).
- `OPERATOR_CHAT_URL` — URL для кнопки «Написать юристу прямо сейчас». По умолчанию используется `max://user/{OPERATOR_USER_ID}`.
- `DB_PATH` — путь к SQLite (по умолчанию `./data/bot.db`).
- `MODE` — `polling` (по умолчанию) или `webhook`.
- `WEBHOOK_URL` — URL вебхука (нужно при `MODE=webhook`).
- `WEBHOOK_SECRET` — секрет для проверки вебхука (опционально).
- `PORT` — порт для вебхука (по умолчанию 8080).

## Локальный запуск (polling)

```bash
mvn -q -DskipTests package
MAX_ACCESS_TOKEN=... OPERATOR_USER_ID=... java -jar target/maxbot.jar
```

## Docker

Сборка:

```bash
docker build -t max-bot:latest .
```

Запуск (polling):

```bash
docker run --rm \
  -e MAX_ACCESS_TOKEN=... \
  -e OPERATOR_USER_ID=... \
  -e OPERATOR_CHAT_URL=max://user/123456 \
  -v $(pwd)/data:/app/data \
  max-bot:latest
```

Запуск (webhook):

```bash
docker run --rm -p 8080:8080 \
  -e MODE=webhook \
  -e WEBHOOK_URL=https://example.com/webhook \
  -e WEBHOOK_SECRET=change-me \
  -e MAX_ACCESS_TOKEN=... \
  -e OPERATOR_USER_ID=... \
  -v $(pwd)/data:/app/data \
  max-bot:latest
```

## Примечания
- Если кнопка с URL не открывает чат, задайте `OPERATOR_CHAT_URL` на правильную ссылку (глубокая ссылка Max).
- Бот хранит состояние диалогов и заявки в SQLite.
