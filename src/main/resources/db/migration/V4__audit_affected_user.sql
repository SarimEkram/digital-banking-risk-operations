-- V4: track the "affected" user on audit rows, so admins can find every action
-- that involved a given user (not just actions the user performed themselves).
--
-- For TRANSFER_CREATE / TRANSFER_HELD / TRANSFER_APPROVE the recipient is affected.
-- For TRANSFER_REJECT the sender is affected (they get the refund).
-- For ADMIN_DEPOSIT the recipient is affected.
-- For PAYEE_ADD / PAYEE_DISABLE / PAYEE_ENABLE the payee user is affected.

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS affected_user_id bigint NULL
        REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_audit_affected_user_id
    ON audit_log(affected_user_id, id);

-- ---- Backfill ----
-- Each statement is idempotent: it only writes where affected_user_id IS NULL,
-- so re-running the migration (manually or via a Flyway repair) cannot overwrite
-- values that have already been set by application code.

-- TRANSFER_CREATE / TRANSFER_HELD / TRANSFER_APPROVE -> recipient (to_account's owner)
UPDATE audit_log a
SET affected_user_id = acc.user_id
FROM transfers t
JOIN accounts acc ON acc.id = t.to_account_id
WHERE a.affected_user_id IS NULL
  AND a.entity_type = 'transfer'
  AND a.entity_id = t.id::text
  AND a.action IN ('TRANSFER_CREATE', 'TRANSFER_HELD', 'TRANSFER_APPROVE', 'ADMIN_DEPOSIT');

-- TRANSFER_REJECT -> sender (from_account's owner)
UPDATE audit_log a
SET affected_user_id = acc.user_id
FROM transfers t
JOIN accounts acc ON acc.id = t.from_account_id
WHERE a.affected_user_id IS NULL
  AND a.entity_type = 'transfer'
  AND a.entity_id = t.id::text
  AND a.action = 'TRANSFER_REJECT';

-- PAYEE_ADD / PAYEE_DISABLE / PAYEE_ENABLE -> the payee user
UPDATE audit_log a
SET affected_user_id = p.payee_user_id
FROM payees p
WHERE a.affected_user_id IS NULL
  AND a.entity_type = 'payee'
  AND a.entity_id = p.id::text
  AND a.action IN ('PAYEE_ADD', 'PAYEE_DISABLE', 'PAYEE_ENABLE');