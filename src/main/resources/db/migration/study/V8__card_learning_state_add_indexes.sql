-- card_learning_states 복합 인덱스.

-- 덱 일일학습 집계 및 조회
CREATE INDEX idx_cls_deck_status_deleted_nextreview
    ON card_learning_states (deck_id, status, deleted_at, next_review_at);

-- user 기준 휴식일 EXISTS
CREATE INDEX idx_cls_user_status_deleted_nextreview
    ON card_learning_states (user_id, status, deleted_at, next_review_at);
