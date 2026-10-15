-- A Telegram bot is becoming a client of the service, and it knows a person
-- only by their Telegram username. Optional: nobody has to use the bot.
-- Stored with its leading "@", as Telegram displays it.
ALTER TABLE users ADD COLUMN telegram_username VARCHAR(33);

-- Telegram usernames are case-insensitive, so uniqueness is too. Partial, like
-- the email index, so a soft-deleted member never blocks reusing one; it also
-- serves the bot's lookup by username.
CREATE UNIQUE INDEX users_telegram_username_uk ON users (lower(telegram_username))
    WHERE is_deleted = FALSE AND telegram_username IS NOT NULL;
