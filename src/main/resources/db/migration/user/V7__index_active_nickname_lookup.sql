ALTER TABLE `users`
    ADD INDEX `idx_users_nickname_deleted_at` (`nickname`, `deleted_at`);
