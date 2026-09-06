-- 전역 덱 프리셋
-- id는 1로 고정, user_id는 null
INSERT INTO `deck_presets` (
    id, user_id, name,
    new_per_day, new_fair_interval, new_easy_interval, new_initial_ease_factor,
    review_per_day, review_max_interval,
    lapse_interval_multiplier, lapse_min_interval,
    leech_threshold, hard_badge_threshold
)
VALUES (
    1, NULL, 'default preset',
    20, 1, 4, 2.50,
    40, 36500,
    0.40, 1,
    8, 3
)
ON DUPLICATE KEY UPDATE id = id;

-- 기본 note type
-- id는 1로 고정
INSERT INTO `note_types` (id, name, description)
VALUES (1, 'front-back', "앞면과 뒷면을 가진 카드를 위한 타입")
ON DUPLICATE KEY UPDATE id = id;

-- front-back 카드 템플릿
INSERT INTO `card_templates` (
    id, note_type_id,
    required_fields,
    template,
    position
)
VALUES (
    1, 1,
    JSON_ARRAY('front', 'back'),
    JSON_OBJECT('front', 'front', 'back', 'back'),
    0
)
ON DUPLICATE KEY UPDATE id = id;
