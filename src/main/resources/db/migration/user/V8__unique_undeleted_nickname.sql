-- Requires the read-only nickname preflight to report no duplicate groups.
-- No existing nickname or account is renamed or deleted by this migration.
ALTER TABLE `users`
    ADD COLUMN `active_nickname` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (IF(`deleted_at` IS NULL, `nickname`, NULL)) VIRTUAL,
    ADD CONSTRAINT `uk_users_active_nickname` UNIQUE (`active_nickname`);
