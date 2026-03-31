-- ============================================================
-- USER
-- ===========================================================
CREATE TABLE `users`(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    nickname        VARCHAR(50)  NOT NULL,
    primary_email   VARCHAR(255) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUSPENDED',
    is_terms_agreed BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '서비스 약관 동의 여부',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    -- soft delete 시에도 active 사용자 간 이메일 유니크 보장
    active_primary_email VARCHAR(255) AS (IF(deleted_at IS NULL, primary_email, NULL)) VIRTUAL,
    UNIQUE KEY uk_active_primary_email (active_primary_email),

    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='서비스 사용자';

-- ============================================================
-- SOCIAL_ACCOUNT
-- USER와 1:1. 멀티 소셜 로그인 확장 시 uk_social_account_user 제거.
-- ============================================================
CREATE TABLE `social_accounts`(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_social_account_user (user_id),
    UNIQUE KEY uk_provider_user (provider, provider_user_id),

    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES `users`(id),

    CONSTRAINT chk_provider_type CHECK (provider IN ('GOOGLE', 'APPLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='소셜 로그인 계정';
