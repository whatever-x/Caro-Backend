-- review_logs 사용자 인덱스.

-- 학습 리포트의 기준 시점(max id)과 사용자 범위 집계
CREATE INDEX idx_log_user_id
    ON review_logs (user_id, id);
