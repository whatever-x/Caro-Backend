-- ============================================================
-- CARD_LEARNING_STATE
-- SM-2 스케줄링 상태. study 모듈이 소유한다.
-- CARD와 1:1 관계 (uk_cls_card로 보장).
-- FSRS 전환 시 이 테이블에 fsrs_stability, fsrs_difficulty 추가.
-- ============================================================
CREATE TABLE IF NOT EXISTS `card_learning_states` (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    -- SM-2 상태
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW'  COMMENT '학습 상태. NEW→REVIEW→SUSPENDED',
    previous_status VARCHAR(20) NULL                    COMMENT 'SUSPENDED 진입 전 상태. 해제 시 복원용',

    -- SM-2 스케줄링
    interval_days    INT          NOT NULL DEFAULT 0    COMMENT '현재 복습 간격(일)',
    repetitions      INT          NOT NULL DEFAULT 0    COMMENT '연속 성공 횟수. AGAIN 시 0으로 리셋',
    ease_factor      DECIMAL(3,2) NOT NULL DEFAULT 2.50 COMMENT '난이도 계수. AGAIN:-0.20, FAIR:±0.00, EASY:+0.15',
    lapses           INT          NOT NULL DEFAULT 0    COMMENT 'REVIEW에서 AGAIN 횟수. leech_threshold 도달 시 SUSPENDED',

    -- NEW 상태 전용
    new_again_count INT NOT NULL DEFAULT 0 COMMENT 'NEW 상태 AGAIN 누적. 3회 시 강제 졸업',

    -- 스케줄링 시각
    next_review_at   DATETIME(6) NULL COMMENT '다음 복습 예정 시각',
    last_reviewed_at DATETIME(6) NULL COMMENT '마지막 복습 시각',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- 1:1 보장
    UNIQUE KEY uk_ls_card (card_id),

    -- SM-2 CHECK 제약
    CONSTRAINT chk_ls_status        CHECK (status IN ('NEW', 'REVIEW', 'SUSPENDED')),
    CONSTRAINT chk_ls_prev_status   CHECK (previous_status IN ('NEW', 'REVIEW', 'SUSPENDED') OR previous_status IS NULL),
    CONSTRAINT chk_ls_interval      CHECK (interval_days >= 0),
    CONSTRAINT chk_ls_repetitions   CHECK (repetitions >= 0),
    CONSTRAINT chk_ls_ease          CHECK (ease_factor BETWEEN 1.30 AND 5.00),
    CONSTRAINT chk_ls_lapses        CHECK (lapses >= 0),
    CONSTRAINT chk_ls_new_again     CHECK (new_again_count >= 0)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='카드 학습 상태 (SM-2). study 모듈 소유. CARD와 1:1';

-- ============================================================
-- STUDY_SESSION
-- 일일 한도 추적의 핵심 테이블.
-- 오늘의 한도 = preset 값 - SUM(오늘 세션들의 studied 카운터).
--
-- study_type:
--   DAILY     = 오늘 범위 학습. new_per_day, review_per_day 한도 적용.
--   FULL_DECK = 전체 덱 학습. 한도 미적용.
-- ============================================================
CREATE TABLE IF NOT EXISTS `study_sessions` (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    deck_id BIGINT NOT NULL,

    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    study_type VARCHAR(20) NOT NULL                               COMMENT 'DAILY=한도 적용, FULL_DECK=한도 미적용',
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at   DATETIME(6) NULL,

    new_cards_studied    INT NOT NULL DEFAULT 0 COMMENT '이 세션에서 학습한 NEW 카드 수',
    review_cards_studied INT NOT NULL DEFAULT 0 COMMENT '이 세션에서 학습한 REVIEW 카드 수',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT chk_session_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'STOPPED')),
    CONSTRAINT chk_session_type CHECK (study_type IN ('DAILY', 'FULL_DECK')),

    CONSTRAINT chk_session_new_count    CHECK (new_cards_studied >= 0),
    CONSTRAINT chk_session_review_count CHECK (review_cards_studied >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='학습 세션. 일일 한도 추적 및 학습 통계';

-- ============================================================
-- REVIEW_LOG
-- Append-only. 수정/삭제하지 않는다.
-- FSRS 전환 시 개인화 파라미터 최적화에 전체 히스토리가 필요하므로,
-- 모든 복습 이벤트를 prev_* / 현재 값 쌍으로 영구 보존한다.
-- ============================================================
CREATE TABLE IF NOT EXISTS `review_logs` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    study_session_id BIGINT NOT NULL,
    card_id          BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,

    rating      VARCHAR(20) NOT NULL COMMENT '평가 버튼. AGAIN/FAIR/EASY',
    time_ms     INT NOT NULL COMMENT '응답 시간(ms). 카드 표시 ~ 평가 버튼 클릭',
    review_type VARCHAR(20) NOT NULL COMMENT '복습 시점의 카드 학습 상태. NEW/REVIEW',

    -- 복습 전 상태
    previous_interval_days INT           NOT NULL COMMENT '복습 전 간격(일)',
    previous_ease_factor   DECIMAL(3, 2) NOT NULL COMMENT '복습 전 EF',
    previous_card_status   VARCHAR(20)   NOT NULL COMMENT '복습 전 카드 학습 상태',

    -- 복습 후 상태
    interval_days INT NOT NULL COMMENT '복습 후 간격(일)',
    ease_factor   DECIMAL(3, 2) NOT NULL COMMENT '복습 후 EF',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_log_session FOREIGN KEY (study_session_id) REFERENCES `study_sessions`(id),

    CONSTRAINT chk_log_time CHECK (time_ms >= 0 AND time_ms <= 600000),
    CONSTRAINT chk_log_previous_interval CHECK (previous_interval_days >= 0),
    CONSTRAINT chk_log_previous_ease CHECK (previous_ease_factor BETWEEN 1.30 AND 5.00),
    CONSTRAINT chk_log_interval CHECK (interval_days >= 0),
    CONSTRAINT chk_log_ease CHECK (ease_factor BETWEEN 1.30 AND 5.00),

    CONSTRAINT chk_log_rating CHECK (rating IN ('EASY', 'FAIR', 'AGAIN')),
    CONSTRAINT chk_log_previous_status CHECK (previous_card_status IN ('NEW', 'REVIEW', 'SUSPENDED')),
    CONSTRAINT chk_log_review_type CHECK (review_type IN ('NEW', 'REVIEW'))
)  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='복습 기록. Append-only';
