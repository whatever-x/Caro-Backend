ALTER TABLE library_copy_receipts
    ADD UNIQUE KEY uk_library_copy_ordinal (user_id, library_deck_id, ordinal);
