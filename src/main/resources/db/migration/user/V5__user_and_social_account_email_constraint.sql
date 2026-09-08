ALTER TABLE `social_accounts`
    DROP INDEX uk_social_accounts_hashed_email;

ALTER TABLE `users`
    DROP INDEX uk_users_active_hashed_primary_email,
    DROP COLUMN active_hashed_primary_email,
    ADD INDEX idx_users_hashed_primary_email (hashed_primary_email);
