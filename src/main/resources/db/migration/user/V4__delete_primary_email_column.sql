ALTER TABLE `users`
    DROP KEY uk_active_primary_email,
    DROP COLUMN active_primary_email,
    DROP COLUMN primary_email;
