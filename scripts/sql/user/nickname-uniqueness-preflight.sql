-- Read-only preflight. Return aggregate counts only; do not rename or delete accounts.
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_SET_NAME, COLLATION_NAME, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('nickname', 'deleted_at');

SELECT
    (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL) AS undeleted_account_count,
    COUNT(*) AS duplicate_nickname_groups,
    COALESCE(SUM(nickname_count - 1), 0) AS accounts_requiring_resolution
FROM (
    SELECT COUNT(*) AS nickname_count
    FROM users
    WHERE deleted_at IS NULL
    GROUP BY nickname COLLATE utf8mb4_unicode_ci
    HAVING COUNT(*) > 1
) AS duplicate_groups;
