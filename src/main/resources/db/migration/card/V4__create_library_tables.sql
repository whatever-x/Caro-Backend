CREATE TABLE library_decks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_library_published_order (published, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE library_cards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    library_deck_id BIGINT NOT NULL,
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_library_card_deck FOREIGN KEY (library_deck_id) REFERENCES library_decks(id),
    INDEX idx_library_card_order (library_deck_id, position, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Receipts survive personal-deck deletion so cumulative numbering and replay remain stable.
CREATE TABLE library_copy_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    library_deck_id BIGINT NOT NULL,
    ordinal BIGINT NULL,
    deck_id BIGINT NULL,
    name VARCHAR(50) NULL,
    description VARCHAR(500) NULL,
    card_count INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_library_copy_user_key (user_id, request_key),
    INDEX idx_library_copy_number (user_id, library_deck_id, deck_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
