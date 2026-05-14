-- ============================================================
-- DEV SEED: DeckPreset (전역 기본 프리셋, id=1 고정)
-- DeckPresetEventListener가 findById(1L)로 참조
-- ============================================================
INSERT INTO `deck_presets` (id, user_id, name)
VALUES (1, NULL, '기본 프리셋')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- DEV SEED: NoteType - Basic (양방향)
-- ============================================================
INSERT INTO `note_types` (id, name, description)
VALUES (1, 'Basic (양방향)', '앞→뒤, 뒤→앞 두 장의 카드를 생성합니다')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- DEV SEED: CardTemplate - Basic 양방향 (2개)
-- position 0: front → back
-- position 1: back  → front
-- ============================================================
INSERT INTO `card_templates` (id, note_type_id, required_fields, template, position)
VALUES
    (1, 1, '["front","back"]', '{"front":"{{front}}","back":"{{back}}"}', 0),
    (2, 1, '["front","back"]', '{"front":"{{back}}","back":"{{front}}"}', 1)
ON DUPLICATE KEY UPDATE note_type_id = note_type_id;
