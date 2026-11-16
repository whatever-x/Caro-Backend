-- 두 번째 ALTER가 실패했을 때 첫 ALTER를 되돌리려면: ALTER TABLE event_publication_archive MODIFY COLUMN SERIALIZED_EVENT VARCHAR(4000) NOT NULL;
-- 이벤트가 완료되면 archive 테이블로 복사되므로, archive 테이블의 컬럼을 먼저 확장한다.
-- metadata lock 대기 기본값은 1년이므로, 문제 발생 시 빠르게 실패하도록 30초로 설정한다.
SET SESSION lock_wait_timeout = 30;

ALTER TABLE event_publication_archive
    MODIFY COLUMN SERIALIZED_EVENT MEDIUMTEXT NOT NULL;

ALTER TABLE event_publication
    MODIFY COLUMN SERIALIZED_EVENT MEDIUMTEXT NOT NULL;

-- Flyway가 앱 커넥션 풀을 쓰므로 세션 값을 서버 기본값으로 되돌린다.
SET SESSION lock_wait_timeout = DEFAULT;
