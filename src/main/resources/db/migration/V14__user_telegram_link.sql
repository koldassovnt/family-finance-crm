-- The Telegram bot links a person when they send /start: it finds them by the
-- username V13 added, then keys everything on Telegram's numeric ids, which
-- unlike a username never change. The chat id is where reminders are sent.
-- Both stay null until that first /start.
ALTER TABLE users
    ADD COLUMN telegram_user_id BIGINT,
    ADD COLUMN telegram_chat_id BIGINT;

-- One Telegram account speaks for at most one live user; also the bot's
-- lookup on every request it makes on someone's behalf.
CREATE UNIQUE INDEX users_telegram_user_id_uk ON users (telegram_user_id)
    WHERE is_deleted = FALSE AND telegram_user_id IS NOT NULL;
