ALTER TABLE deck_presets
    ADD COLUMN hard_badge_threshold INT NOT NULL DEFAULT 3
        COMMENT 'HARD 뱃지 판정을 위한 연속 AGAIN 평가 임계치';
