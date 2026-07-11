ALTER TABLE card_learning_states
    DROP COLUMN last_reviewed_at,
    DROP COLUMN next_review_at,

    ADD COLUMN last_reviewed_date DATE COMMENT '마지막 복습일',
    ADD COLUMN next_review_date DATE COMMENT '다음 복습 예정일';
