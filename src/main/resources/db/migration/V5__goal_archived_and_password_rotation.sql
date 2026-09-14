-- Goals gain ARCHIVED (done with, keep the record), and only ACTIVE ones block
-- deleting their linked account.
ALTER TABLE goals DROP CONSTRAINT goals_status_check;
ALTER TABLE goals ADD CONSTRAINT goals_status_check
    CHECK (status IN ('ACTIVE', 'ABANDONED', 'ARCHIVED'));

-- There is no server-side token store, so this column is the only revocation
-- available: the JWT filter rejects any token issued before it. Changing a
-- password logs every other session out.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
UPDATE users SET password_changed_at = created_at;
ALTER TABLE users ALTER COLUMN password_changed_at SET NOT NULL;
