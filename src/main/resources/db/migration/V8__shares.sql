-- Phase 8 — sharing: one member grants another read access to one specific
-- thing they own. Additive by design: nothing about a resource changes when it
-- is shared, so no existing table is touched.

CREATE TABLE shares (
    id            UUID         PRIMARY KEY,
    resource_type VARCHAR(16)  NOT NULL,
    -- No FK: it points at one of five tables. Nothing here is ever hard-deleted,
    -- so a share cannot be orphaned by a vanishing row, and the service resolves
    -- the resource and checks ownership before writing.
    resource_id   UUID         NOT NULL,
    -- Denormalized from the resource so "everything I have shared" is one query.
    owner_id      UUID         NOT NULL REFERENCES users (id),
    grantee_id    UUID         NOT NULL REFERENCES users (id),
    access        VARCHAR(16)  NOT NULL,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    -- Sharing with yourself is a mistake, not a grant that happens to be useless.
    CONSTRAINT shares_not_self_check CHECK (owner_id <> grantee_id)
);

-- Sharing the same thing twice with the same person is a no-op, not a second
-- grant. Partial, so revoking and re-sharing later works.
CREATE UNIQUE INDEX shares_resource_grantee_uk
    ON shares (resource_type, resource_id, grantee_id) WHERE is_deleted = FALSE;

-- Every viewer-side read filters on these two.
CREATE INDEX shares_grantee_type_idx ON shares (grantee_id, resource_type);
CREATE INDEX shares_owner_idx ON shares (owner_id);
