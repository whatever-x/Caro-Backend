-- Spring Modulith 2.0.x requires BINARY(16) for id and camelCase column names
RENAME
TABLE EVENT_PUBLICATION TO event_publication;
RENAME
TABLE EVENT_PUBLICATION_ARCHIVE TO event_publication_archive;

ALTER TABLE event_publication
    MODIFY COLUMN ID BINARY(16) NOT NULL;

ALTER TABLE event_publication_archive
    MODIFY COLUMN ID BINARY(16) NOT NULL;
