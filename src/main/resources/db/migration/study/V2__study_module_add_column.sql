ALTER TABLE study_sessions
    ADD COLUMN estimated_total INT NOT NULL DEFAULT 0
        COMMENT 'min(NEW 후보, new_per_day) + min(REVIEW 후보, review_per_day). 시작 시 stamp, 카드 삭제 이벤트 시 동적 감소',

    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC'
        COMMENT 'IANA timezone (예: Asia/Seoul)',

    ADD COLUMN day_cutoff_hour TINYINT NOT NULL DEFAULT 4
        COMMENT '하루 시작 시각 (0~23)',

    ADD COLUMN session_date DATE NOT NULL DEFAULT '2026-05-03'
        COMMENT '사용자 timezone과 day_cutoff_hour를 적용한 실제 학습일',

    ADD CONSTRAINT chk_session_estimated_total CHECK (estimated_total >= 0),
    ADD CONSTRAINT chk_session_cutoff_hour CHECK (day_cutoff_hour BETWEEN 0 AND 23);

ALTER TABLE card_learning_states
    ADD COLUMN consecutive_again_count INT NOT NULL DEFAULT 0
        COMMENT '카드의 연속 AGAIN 횟수. EASY/FAIR 평가 시 0으로 리셋.',

    ADD COLUMN total_reviews INT NOT NULL DEFAULT 0
        COMMENT '누적 평가 횟수.',

    ADD CONSTRAINT chk_ls_consecutive_again_count CHECK (consecutive_again_count >= 0),
    ADD CONSTRAINT chk_ls_total_reviews CHECK (total_reviews >= 0);
