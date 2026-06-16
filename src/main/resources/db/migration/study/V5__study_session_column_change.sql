ALTER TABLE study_sessions
    DROP CONSTRAINT chk_session_estimated_total,
    DROP COLUMN estimated_total,

    ADD COLUMN new_cards_goal INT NOT NULL DEFAULT 0
        COMMENT 'min(NEW 후보, new_per_day). 시작 시 stamp, 카드 삭제 이벤트 시 동적 감소',
    ADD COLUMN review_cards_goal INT NOT NULL DEFAULT 0
        COMMENT 'min(REVIEW 후보, review_per_day). 시작 시 stamp, 카드 삭제 이벤트 시 동적 감소',

    ADD CONSTRAINT chk_session_new_cards_goal CHECK (new_cards_goal >= 0),
    ADD CONSTRAINT chk_session_review_cards_goal CHECK (review_cards_goal >= 0),

    ADD CONSTRAINT uk_session_per_day UNIQUE (user_id, deck_id, session_date);
