-- decks.description: NULL → NOT NULL DEFAULT ''
UPDATE `decks` SET description = '' WHERE description IS NULL;
ALTER TABLE `decks`
    MODIFY COLUMN description VARCHAR(500) NOT NULL DEFAULT '';

-- cards.position 컬럼 제거
ALTER TABLE `cards`
    DROP COLUMN position;
