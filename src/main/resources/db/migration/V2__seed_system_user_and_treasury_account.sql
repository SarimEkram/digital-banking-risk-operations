-- Seed a "system" user + a CAD treasury account (bank-owned).
-- This is the source of funds when admins "insert money" into customer accounts.

WITH sys_user AS (
    INSERT INTO users (email, password_hash, role)
    VALUES (
        'system@bank.local',
        -- bcrypt hash for the string "disabled" (doesn't matter; we won't log in as this user)
        '$2a$10$7Yj2Xy4Q0Gk8fH5h4J8p7O8v7v0Q8YwqXK8x3J1y1xQ0qk3p0rP9e',
        'ADMIN'
    )
    ON CONFLICT (email) DO UPDATE SET role = EXCLUDED.role
    RETURNING id
),
sys_id AS (
    SELECT id FROM sys_user
    UNION ALL
    SELECT id FROM users WHERE email = 'system@bank.local' LIMIT 1
)
INSERT INTO accounts (user_id, currency, balance_cents, status)
SELECT id, 'CAD', 0, 'ACTIVE'
FROM sys_id
WHERE NOT EXISTS (
    SELECT 1
    FROM accounts a
    JOIN users u ON u.id = a.user_id
    WHERE u.email = 'system@bank.local'
      AND a.currency = 'CAD'
);
