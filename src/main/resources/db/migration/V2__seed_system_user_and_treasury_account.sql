-- V2: Seed a "system" user + a funded CAD treasury account (bank-owned).
-- Source of funds when admins "insert money" into customer accounts.

WITH sys_user AS (
    INSERT INTO users (email, password_hash, role)
    VALUES (
        'system@bank.local',
        '$2a$10$7Yj2Xy4Q0Gk8fH5h4J8p7O8v7v0Q8YwqXK8x3J1y1xQ0qk3p0rP9e',
        'ADMIN'
    )
    ON CONFLICT (email) DO UPDATE SET role = EXCLUDED.role
    RETURNING id
),
sys_existing AS (
    SELECT id FROM users WHERE email = 'system@bank.local'
),
sys_id AS (
    SELECT id FROM sys_user
    UNION
    SELECT id FROM sys_existing
)
INSERT INTO accounts (user_id, currency, balance_cents, status)
SELECT id, 'CAD', 100000000, 'ACTIVE'   -- $1,000,000.00 for dev
FROM sys_id
WHERE NOT EXISTS (
    SELECT 1
    FROM accounts a
    JOIN users u ON u.id = a.user_id
    WHERE u.email = 'system@bank.local'
      AND a.currency = 'CAD'
);

-- Top-up if it already existed (dev convenience)
UPDATE accounts a
SET balance_cents = 100000000
FROM users u
WHERE u.id = a.user_id
  AND u.email = 'system@bank.local'
  AND a.currency = 'CAD'
  AND a.balance_cents < 100000000;
