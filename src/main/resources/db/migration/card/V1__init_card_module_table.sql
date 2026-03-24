-- ============================================================
-- DECK_PRESET
-- SM-2 학습 설정 프리셋. 여러 덱이 하나의 프리셋을 공유할 수 있다.
-- 모든 덱들이 기본적으로 참고하는 전역 프리셋을 생성해야함.
-- 프리셋 값 변경 시 해당 프리셋을 사용하는 모든 덱에 일괄 적용.
-- ============================================================
CREATE TABLE IF NOT EXISTS `deck_presets`(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,

    name VARCHAR(100) NOT NULL,

    -- 신규 카드 설정
    new_per_day             INT           NOT NULL DEFAULT 20   COMMENT '학습 시 새 카드 한도',
    new_fair_interval       INT           NOT NULL DEFAULT 1    COMMENT 'FAIR 졸업 시 최초 간격(일)',
    new_easy_interval       INT           NOT NULL DEFAULT 4    COMMENT 'EASY 졸업 시 최초 간격(일)',
    new_initial_ease_factor DECIMAL(3, 2) NOT NULL DEFAULT 2.50 COMMENT '새 카드 초기 EF',

    -- 복습 설정
    review_per_day      INT NOT NULL DEFAULT 40    COMMENT '학습 시 복습 카드 한도',
    review_max_interval INT NOT NULL DEFAULT 36500 COMMENT '최대 복습 간격(일). 36500==100년',

    -- 실패(Lapse) 설정
    lapse_interval_multiplier DECIMAL(3, 2) NOT NULL DEFAULT 0.4 COMMENT 'AGAIN 시 이전 간격 유지 비율. 0.00=초기화, 0.50=절반 유지',
    lapse_min_interval        INT           NOT NULL DEFAULT 1   COMMENT 'Lapse 후 최소 간격(일)',

    -- Leech 설정
    leech_threshold INT NOT NULL DEFAULT 8 COMMENT 'Leech 판정 lapse 횟수. 도달 시 SUSPENDED',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT chk_deck_preset_new_per_day         CHECK (new_per_day >= 0),
    CONSTRAINT chk_deck_preset_new_fair_interval   CHECK (new_fair_interval >= 1),
    CONSTRAINT chk_deck_preset_new_easy_interval   CHECK (new_easy_interval >= 1),
    CONSTRAINT chk_deck_preset_new_initial_ease    CHECK (new_initial_ease_factor BETWEEN 1.30 AND 5.00),
    CONSTRAINT chk_deck_preset_review_per_day      CHECK (review_per_day >= 0),
    CONSTRAINT chk_deck_preset_review_max_interval CHECK (review_max_interval >= 1),
    CONSTRAINT chk_deck_preset_lapse_multiplier    CHECK (lapse_interval_multiplier BETWEEN 0.00 AND 1.00),
    CONSTRAINT chk_deck_preset_lapse_min_interval  CHECK (lapse_min_interval >= 1),
    CONSTRAINT chk_deck_preset_leech_threshold     CHECK (leech_threshold >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SM-2 학습 설정 프리셋. 덱 간 공유 가능';


-- ============================================================
-- DECK
-- MVP 단계에서는 모든 덱의 프리셋은 전역 프리셋을 참조.
-- ============================================================
CREATE TABLE IF NOT EXISTS `decks`(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    deck_preset_id BIGINT NULL,

    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    card_count  INT          NOT NULL DEFAULT 0 COMMENT '비정규화. 카드 CUD 시 TX 내 동기화 필수',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    CONSTRAINT fk_deck_deck_preset FOREIGN KEY (deck_preset_id) REFERENCES `deck_presets`(id),

    CONSTRAINT chk_deck_card_count CHECK (card_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='카드 덱';

-- ============================================================
-- NOTE_TYPE
-- 시스템 전역 제공. user_id 없음 (사용자 커스텀 타입 미지원).
-- ============================================================
CREATE TABLE IF NOT EXISTS `note_types` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='노트 유형. 시스템 제공 (Basic 등). 템플릿 그룹 역할';

-- ============================================================
-- CARD_TEMPLATE
-- NOTE_TYPE에 소속되며, 카드 렌더링(앞/뒷면)을 정의한다.
-- required_fields: 이 템플릿이 참조하는 필드 목록. 카드 생성 시 런타임 검증에 사용.
-- ============================================================
CREATE TABLE IF NOT EXISTS `card_templates` (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_type_id BIGINT NOT NULL,

    required_fields JSON NOT NULL COMMENT '템플릿이 참조하는 필드 목록. 카드 생성 시 런타임 검증용도',
    template        JSON NOT NULL COMMENT '렌더링 템플릿. 예시1: {"front":"{{front}}","back":"{{front}}<hr>{{back}}"}',
    position        INT  NOT NULL COMMENT 'note_type 내 정렬 순서',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_card_template_note_type FOREIGN KEY (note_type_id) REFERENCES `note_types`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='카드 렌더링 템플릿. NOTE_TYPE에 소속';

-- ============================================================
-- NOTE
-- NOTE_TYPE과 직접 연결하지 않음 (note_type_id FK 없음).
-- 이유:
--   동일 필드 구조(front, back)를 사용하는 서로 다른 NOTE_TYPE의 템플릿을
--   자유롭게 교체할 수 있도록. 필드 호환성은 카드 생성 시 런타임 검증.
-- ============================================================
CREATE TABLE IF NOT EXISTS `notes` (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    fields JSON NOT NULL COMMENT '필드 데이터. 예: {"front":"apple", "back":"사과"}',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='노트. 카드의 콘텐츠 원본. 1 노트 → N 카드';

-- ============================================================
-- CARD
-- SM-2 스케줄링 상태를 직접 포함 (CARD_LEARNING_STATE 미분리, MVP 결정).
-- FSRS 전환 시 card 테이블 스키마 변경 또는 별도 상태 테이블 분리 필요.
--
-- 상태 전이:
--   NEW → REVIEW (졸업) / NEW → SUSPENDED (없음)
--   REVIEW → REVIEW (FAIR/EASY) / REVIEW → SUSPENDED (Leech)
--
-- EF 변동 순서:
--   FAIR/EASY는 EF 먼저 변동 후 interval 계산.
--   AGAIN은 lapse_multiplier로 별도 계산, EF 미관여.
-- ============================================================
CREATE TABLE IF NOT EXISTS `cards` (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_template_id BIGINT NOT NULL,
    note_id          BIGINT NOT NULL,
    deck_id          BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,

    position INT NOT NULL DEFAULT 0  COMMENT '덱 내 표시 순서',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,

    CONSTRAINT fk_card_card_template FOREIGN KEY (card_template_id) REFERENCES `card_templates`(id),
    CONSTRAINT fk_card_note FOREIGN KEY (note_id) REFERENCES `notes`(id),
    CONSTRAINT fk_card_deck FOREIGN KEY (deck_id) REFERENCES `decks`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='학습 카드. 콘텐츠 및 소속 정보만 보유';
