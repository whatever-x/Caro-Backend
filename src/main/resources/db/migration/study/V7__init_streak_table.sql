CREATE TABLE IF NOT EXISTS `streak_states` (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT  NOT NULL,
    current_streak      INT     NOT NULL DEFAULT 0  COMMENT '연속 학습 카운트',
    last_recorded_date  DATE    NOT NULL            COMMENT '마지막으로 기록된 날짜(타임존 변환 없이 원본 그대로)',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_streak_states_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='스트릭 상태. USER와 1:1';

CREATE TABLE IF NOT EXISTS `study_days` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    streak_type VARCHAR(20) NOT NULL COMMENT '스트릭 종류',
    study_date  DATE        NOT NULL COMMENT '학습일(타임존 변환 없이 원본 그대로)',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_user_study_date (user_id, study_date),

    CONSTRAINT chk_streak_type CHECK (streak_type IN ('DAILY_STUDY', 'REST_DAY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='스트릭을 구성하는 학습일.';
