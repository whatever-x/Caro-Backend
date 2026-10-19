---
name: add-db-migration
description: Flyway DB 마이그레이션의 작성·검증 절차와 완료 조건을 제공한다. 테이블·컬럼·인덱스·제약 변경, DB에 저장되는 Kotlin enum의 값 추가, 시드(`R__*.sql`) 대상 테이블이나 값 변경, 기존 데이터 보정에 사용.
---

# DB 마이그레이션 추가

먼저 `src/main/resources/db/migration/AGENTS.md`와 대상 모듈의 AGENTS.md를 읽는다.

1. 폴더는 `src/main/resources/db/migration/<module>/`이다. 그 모듈의 폴더가 없으면 만들고 `V1__init_<module>_module_table.sql`로 시작한다.
2. 최대 버전은 `ls src/main/resources/db/migration/<module>/ | sort -V | tail -1`로 확인하고, 새 파일은 `V<max+1>__<snake_case_설명>.sql`로 만든다.
   `ls`만 쓰면 `V10`이 `V2`보다 앞에 정렬된다.
3. 되돌려도 원래 값이 복원되지 않는 변경(`DROP TABLE`·`DROP COLUMN`, 타입 축소, 값 변환 UPDATE)은 SQL을 쓰기 전에 사용자에게 확인받는다.
   적용되면 staging·prod의 기존 값이 지워지고 이 파일을 되돌려도 복원되지 않는다.
4. SQL은 `db/migration/AGENTS.md`의 작성 규칙(제약 이름, 타입, 시각 컬럼, NOT NULL 도입, `MODIFY`)대로 쓰고, DDL이 둘 이상이면 되돌림 주석을 이 파일을 처음 쓸 때 넣는다.
   Kotlin enum에 값을 추가했으면 `db/migration/AGENTS.md`의 enum 항목대로 그 enum을 저장하는 모든 컬럼의 CHECK를 이 파일에서 교체한다.
5. 같은 변경에서 SQL 밖의 파일도 고친다.
   - 엔티티 매핑이 바뀌면 엔티티를 고친다. `created_at`·`updated_at`·`deleted_at`은 필드로 선언하지 않고 `common/entity`의 `BaseTimeEntity`·`SoftDeletableEntity`를 상속한다.
   - 그 테이블을 저장·조회하는 리포지토리 통합 테스트가 없으면 추가한다.
   - `user_id` 컬럼이 있는 테이블을 만들거나 `user_id`를 추가하면 `db/migration/AGENTS.md`의 `user_id` 항목대로 탈퇴 파기 경로를 고친다.
   - 시드(`db/seed/<module>/R__*.sql`)가 INSERT하는 테이블을 바꾸면 시드도 고친다. 기존 행의 값을 바꾸려면 새 `V` 파일의 UPDATE와 시드 값을 함께 고친다.
6. `./gradlew test --tests "com.whatever.caro.<module>.*"`로 검증한다. 대상 모듈 AGENTS.md에 `--tests` 대상이 있으면 그것도 넣는다.
   시드를 고쳤으면 그 시드 폴더 모듈의 테스트를, `user_id` 파기 경로를 고쳤으면 `com.whatever.caro.withdrawal.*`도 넣는다.
   자기 `@CaroModuleTest`가 없는 모듈은 `db/migration/AGENTS.md`의 모듈 테스트 항목대로 이 SQL을 적용하는 테스트를 고른다.
7. 새 SQL이 적용됐는지 확인한다. 폴더 이름이 틀린 SQL은 오류 없이 무시되고 테스트는 통과한다.
   `grep -oh 'to version "[^"]*"' build/test-results/test/TEST-*.xml | sort -u`에 새 파일의 버전과 설명이 함께 나와야 한다. 다른 모듈의 같은 번호도 나오므로 설명으로 대조한다.
   나오지 않으면 폴더 이름을 그 모듈 `ModuleMetadata.kt`의 패키지 이름과 글자 단위로 비교한다.
8. `make check`를 통과시킨다. 로컬 DB 적용 확인은 필수가 아니며, 하려면 `make run`을 백그라운드로 실행해 기동이 성공하는지 확인한 뒤 종료한다.
   Docker나 Infisical 로그인이 없어 `make run`을 할 수 없으면 확인하지 못한 항목으로 보고한다.
   적용이 중간에 실패하면 로컬 이력에 `success=0` 행이 남아 다음 기동도 실패하므로, 복구 방법은 사용자에게 보고해 정한다.
   `make reset`은 로컬 MySQL·Redis 데이터를 모두 지우므로 사용자에게 확인받은 뒤에만 실행한다. staging·prod에는 없는 수단이므로 PR 설명의 복구 방안으로 쓰지 않는다.
9. PR을 올리기 전에 `git fetch origin develop`을 실행하고, `origin/develop`의 같은 폴더에 같은 번호가 먼저 병합됐으면 새 파일의 번호를 올린다.
   같은 폴더에 같은 번호 파일이 둘이면 Flyway가 기동 시 실패한다.

## 완료 조건
- 새 `V` 파일이 있고 `develop`에 이미 있는 `V` 파일은 바뀌지 않았다. `git diff --name-status --diff-filter=MRD "$(git merge-base origin/develop HEAD)" -- 'src/main/resources/db/migration/*/V*.sql'`의 출력이 비어야 한다
- 6단계 테스트와 `make check`를 통과하고 7단계 grep에 새 파일이 나온다
- 4·5단계의 해당 항목(enum CHECK, 엔티티와 리포지토리 테스트, `user_id` 파기 경로, 시드)을 같은 변경에 반영했다
- PR 설명에 다음을 적는다
  - 3단계 사용자 확인 결과와, 변경이 어긋나면 실패할 테스트 이름
  - 기존 행이 있을 때만 실패하는 SQL이면 위반하는 기존 행을 세는 `SELECT`
  - 복원되지 않는 변경이면 복원되지 않는 컬럼과 값
