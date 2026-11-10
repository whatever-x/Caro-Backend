-- ============================================================
-- REVIEW_REMINDER_SETTINGS
-- 유저별 복습 알림 설정(유저당 1행, upsert).
-- reminder_hour는 유저 기기 타임존 기준 현지 시(hour)다. 해제는 enabled=0으로 둔다.
-- ============================================================
CREATE TABLE IF NOT EXISTS `review_reminder_settings` (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT     NOT NULL,
    enabled       TINYINT(1) NOT NULL COMMENT '복습 알림 수신 여부',
    reminder_hour TINYINT    NOT NULL COMMENT '현지 시각 기준 발송 시(0~23)',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_review_reminder_user (user_id),
    KEY idx_review_reminder_enabled_hour (enabled, reminder_hour),

    CONSTRAINT chk_review_reminder_hour CHECK (reminder_hour BETWEEN 0 AND 23)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='복습 알림 설정';
