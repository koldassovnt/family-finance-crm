-- Bootstraps the single OWNER account.
--
-- There is deliberately no endpoint for this: at the point the first user is
-- created there is nobody to authorize the call. Run this once, by hand,
-- against the database after the Flyway migrations have applied. Every other
-- user is created afterwards through POST /api/v1/users.
--
-- Generate the password hash first:
--     ./gradlew printPasswordHash -Ppassword='your-password'
--
-- Then substitute the three values below and run:
--     psql "$DB_URL" -f db/bootstrap-owner.sql

INSERT INTO users (
    id, email, display_name, password_hash, role,
    -- NOT NULL since V5: tokens issued before this instant are rejected.
    password_changed_at,
    is_deleted, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'you@example.com',
    'Your Name',
    '$2a$10$REPLACE_WITH_THE_GENERATED_HASH',
    'OWNER',
    now(),
    FALSE,
    now(),
    now()
);
