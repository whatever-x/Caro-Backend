ALTER TABLE `users`
    ADD COLUMN hashed_primary_email CHAR(44) NULL
          COMMENT 'HMAC_SHA256(정규화 email) base64, 길이 44 고정, blind index 검색용',
    ADD COLUMN active_hashed_primary_email CHAR(44)
        AS (IF(deleted_at IS NULL, hashed_primary_email, NULL)) VIRTUAL,
    ADD CONSTRAINT uk_users_active_hashed_primary_email UNIQUE (active_hashed_primary_email);


ALTER TABLE `social_accounts`
    ADD COLUMN hashed_email CHAR(44) NULL
          COMMENT 'HMAC_SHA256(정규화 email) base64, 길이 44 고정, blind index 검색용',
    ADD CONSTRAINT uk_social_accounts_hashed_email UNIQUE (hashed_email);
