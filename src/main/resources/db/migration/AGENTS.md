# db/migration

- 새 SQL은 `db/migration/<모듈>/`, 시드는 `db/seed/<모듈>/`에 두고, 폴더 이름은 displayName(`Study`)이 아니라 `@ApplicationModule`이 붙은 `ModuleMetadata.kt`의 패키지 이름(`study`)과 글자 단위로 같게 쓴다.
  `spring.modulith.runtime.flyway-enabled: true`라서 Modulith가 `spring.flyway.locations`의 각 경로 끝에 `/<모듈>`을 더해 모듈마다 Flyway를 실행한다.
  그 밖의 경로에 둔 SQL은 적용되지 않고 그 파일에 대한 오류나 경고 없이 기동된다. 대소문자만 다른 폴더는 macOS의 테스트와 `bootRun`에서는 적용되지만 실행 JAR에서는 적용되지 않는다.
- 시드 `R__seed_default_data.sql`은 `ON DUPLICATE KEY UPDATE id = id`로 INSERT하므로 이미 있는 행의 값을 바꾸지 않는다.
  기존 DB의 값을 바꾸려면 시드와 같은 모듈의 `db/migration/<모듈>/`에 새 `V<n>` UPDATE를 추가하고 시드의 값도 같이 고친다. 새 DB에서는 versioned가 시드보다 먼저 실행돼 UPDATE가 0건에 적용되기 때문이다.
- 어느 모듈도 소유하지 않는 인프라 테이블(Modulith의 `event_publication` 계열)은 `__root/`에 두고, 엔티티가 없어도 한 모듈이 읽고 쓰는 테이블은 그 모듈 폴더에 둔다.
  `__root`는 Modulith가 정한 이름이라 다른 이름의 폴더는 적용되지 않는다.
- FK는 같은 폴더가 만든 테이블에만 선언하고, 다른 모듈 폴더의 테이블을 가리키는 `user_id`, `card_id`, `deck_id`는 FK 제약 없는 `BIGINT` 컬럼으로 둔다.
  모듈별 Flyway 실행 순서는 Modulith가 모듈 간 코드 의존으로 정하고(의존에 순환이 있으면 식별자 알파벳 순서), FK의 참조 방향은 반영하지 않는다.
  모듈 간 FK는 참조 대상 테이블이 아직 없는 빈 DB나 참조 대상 모듈을 포함하지 않는 `@CaroModuleTest`에서 `Failed to open the referenced table`로 기동이 실패하고, 이미 테이블이 있는 DB에서는 성공한다.
- `user_id` 컬럼이 있는 테이블을 만들거나 기존 테이블에 `user_id`를 추가하면, 같은 변경에서 소유 모듈의 탈퇴 파기 메서드에 그 테이블의 `user_id` 범위 hard delete를 추가한다.
  파기 메서드는 `deleteAllByUserId`(user는 `UserService.hardDeleteUser`)이고, 새 모듈이면 `src/main/kotlin/com/whatever/caro/withdrawal/AGENTS.md`의 `purge` 규칙을 따른다.
  `WithdrawnUserPurgeServiceTest`는 리포지토리를 직접 나열해 새 테이블을 감지하지 못하므로, 그 테스트의 seed, 존재 검증, `afterEach` 정리 목록에도 그 테이블을 넣는다. 빠뜨리면 탈퇴한 유저의 행이 파기되지 않고 남는다.
- 제약과 인덱스에는 `chk_`, `uk_`, `idx_`, `fk_` 접두사를 붙이고, 기존 테이블이면 그 테이블의 기존 제약 이름에 쓰인 표기를 이어 쓴다.
  CHECK와 FK 이름은 스키마 전체에서 유일해야 해서 겹치면 DDL이 실패하므로, 새 이름은 `grep -rn '<이름>' src/main/resources/db/migration`으로 확인한다.
  MySQL에는 CHECK 식을 바꾸는 구문이 없어 한 `ALTER TABLE` 문에 `DROP CONSTRAINT <이름>, ADD CONSTRAINT <이름> CHECK (...)`를 함께 쓴다. 두 문으로 나누면 ADD가 실패했을 때 DROP만 커밋돼 CHECK가 없는 상태가 된다.
- 상태나 열거 컬럼은 `VARCHAR(20)`에 `CHECK (col IN (...))`으로 허용 값을 제한한다. Kotlin enum에 값을 추가하면 그 enum을 저장하는 모든 컬럼의 CHECK를 새 `V` 파일로 교체한다.
  교체할 CHECK는 `grep -rn "'<기존 enum 값>'" src/main/resources/db/migration`으로 찾는다. 예를 들어 `CardLearningStatus`는 `chk_ls_status`, `chk_ls_prev_status`, `chk_log_previous_status` 세 곳에 쓰인다.
  검색 결과에는 다른 enum의 CHECK와 `DEFAULT` 값도 섞이므로(`'NEW'`는 `chk_log_review_type`, `'SUSPENDED'`는 `chk_user_status`에도 있다), 엔티티에서 그 enum 타입 필드가 매핑된 컬럼의 CHECK만 교체한다.
  일부만 교체하면 교체하지 않은 CHECK의 컬럼에 새 값을 쓰는 요청이 실패한다. `chk_ls_status`만 교체하면 새 값을 가진 카드의 다음 평가에서 `previous_status` UPDATE와 `review_logs` INSERT가 실패한다.
- id는 `BIGINT AUTO_INCREMENT PRIMARY KEY`, 시각은 `DATETIME(6)`, 날짜는 `DATE`를 쓴다.
  엔티티의 `Instant`는 마이크로초까지 저장되는데, 정밀도를 낮추면 MySQL이 소수 초를 반올림해 저장 값이 달라진다. 예를 들어 `DATETIME`(소수 0자리)에서는 23:59:59.5 이후 값이 다음 날 00:00:00이 된다.
- 새 테이블에는 `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`과 `updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)`을 둔다.
  소프트 삭제 테이블에는 `deleted_at DATETIME(6) NULL`을 더하고, 행을 수정하지 않는 append-only 테이블(`review_logs`)에는 `created_at`만 둔다. `BaseTimeEntity`와 `SoftDeletableEntity`가 이 컬럼명으로 매핑한다.
  SQL로 직접 INSERT하는 시드(`R__seed_default_data.sql`)는 시각 컬럼을 생략하므로, `DEFAULT`가 없는 테이블에 시드를 넣으면 `Field 'created_at' doesn't have a default value`로 기동이 실패한다.
- 기존 행이 있는 테이블에 NOT NULL을 도입할 때는 기존 행이 가질 값을 SQL에 직접 적는다. 새 컬럼은 `DEFAULT`와 함께 추가하고(`card/V2`), NULL을 허용하던 컬럼은 `UPDATE`로 채운 뒤 `MODIFY`한다(`card/V3`의 `decks.description` 부분).
  NOT NULL 컬럼을 `DEFAULT` 없이 추가하면 숫자는 0, 문자열은 빈 문자열이 기존 행에 오류 없이 채워지고, `DATE`·`DATETIME`은 strict mode가 `0000-00-00`을 거부해 마이그레이션이 실패한다.
- `MODIFY`는 컬럼 정의 전체를 새로 지정하므로 기존 `NOT NULL`, `DEFAULT`, `ON UPDATE`, `COMMENT`를 함께 적는다. 적지 않으면 오류 없이 `NOT NULL`은 NULL 허용으로 바뀌고 나머지는 지워진다.
  기존 정의는 `SHOW CREATE TABLE`이나 그 컬럼을 마지막으로 바꾼 `V` 파일에서 옮겨 적는다. 처음 만든 파일의 정의는 이후 바뀌었을 수 있다(`decks.description`은 `card/V1`이 NULL 허용, `card/V3`이 NOT NULL).
- 새 SQL은 그 모듈 패키지의 `@CaroModuleTest` 테스트를 실행해 검증한다.
  모듈 테스트는 `__root`, 대상 모듈, 그 테스트의 `@CaroModuleTest`에 지정한 mode 깊이의 의존 모듈과 extraIncludes·shared 모듈의 폴더만 Flyway에 적용하므로, 다른 모듈 테스트가 통과한 것은 그 SQL이 실행됐다는 근거가 아니다.
  자기 모듈 테스트가 없는 모듈의 SQL은 그 모듈을 포함하는 다른 모듈 테스트나 모든 모듈을 적용하는 `CaroApplicationTests`로 검증한다.
- 엔티티와 SQL이 일치하는지는 그 테이블에 저장하고 조회하는 리포지토리 통합 테스트로 확인하고, 없으면 같은 변경에서 추가한다. `ddl-auto: none`이라 불일치해도 컨텍스트 로딩은 성공한다.
  테스트는 Spring 컨텍스트마다 새 빈 DB에 V1부터 적용하므로, UNIQUE·FK 추가나 NULL이 있는 컬럼의 NOT NULL 변경처럼 기존 행이 있을 때만 실패하는 SQL은 테스트를 통과하고 데이터가 있는 DB에서 처음 실패한다.
  이런 SQL은 위반하는 기존 행을 세는 `SELECT`를 PR 설명에 적는다.
- 한 파일에 DDL이 둘 이상이면 앞 DDL을 되돌리는 SQL을 파일을 처음 쓸 때 맨 위 주석에 적는다. 적용된 뒤에는 주석도 체크섬에 포함되므로 고치지 않는다.
  MySQL은 DDL마다 즉시 커밋하므로 중간 DDL이 실패하면 앞 DDL이 적용된 채 이력 테이블에 `success=0` 행이 기록되고, 다음 기동은 `Detected failed migration`으로 실패한다.
  이력 테이블은 모듈 폴더가 `flyway_schema_history_<모듈>`, `__root`가 `flyway_schema_history`다.
- `develop`에 병합된 `V` 파일은 이름을 바꾸거나 지우지 않고, 바꿔야 하면 사용자에게 확인받는다.
  설명만 바꿔도 `Migration description mismatch`로 모든 환경의 기동이 실패한다. 번호를 바꾸면 local은 `Detected applied migration not resolved locally`로 기동이 실패한다.
  prod·staging은 `ignore-migration-patterns: "*:missing"`이라, 새 번호가 그 모듈의 최대 적용 버전보다 크면 SQL을 새 마이그레이션으로 다시 실행하고 그 사이 번호면 `Detected resolved migration not applied to database`로 기동이 실패한다.
