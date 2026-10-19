-- ============================================================
-- DEVICE_TOKEN
-- USER와 1:N (기기마다 토큰 1개). 토큰은 전역 유니크.
-- 같은 기기에서 다른 계정으로 로그인하면 user_id가 재할당된다.
-- ============================================================
CREATE TABLE IF NOT EXISTS `device_tokens` (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    token        VARCHAR(512) NOT NULL COMMENT 'FCM registration token',
    platform     VARCHAR(20)  NOT NULL,
    last_seen_at DATETIME(6)  NOT NULL COMMENT '클라이언트가 마지막으로 토큰을 등록한 시각',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_device_tokens_token (token),
    KEY idx_device_tokens_user (user_id),

    CONSTRAINT chk_device_platform CHECK (platform IN ('ANDROID', 'IOS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='푸시 알림 기기 토큰';
