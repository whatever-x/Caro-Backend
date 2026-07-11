ALTER TABLE `users`
    ADD COLUMN encrypted_primary_email VARCHAR(512) NULL;

ALTER TABLE `social_accounts`
    ADD COLUMN encrypted_email VARCHAR(512) NULL;
