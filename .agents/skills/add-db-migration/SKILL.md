---
name: add-db-migration
description: Flyway DB 마이그레이션의 작성·검증 절차를 제공한다. 테이블·컬럼·인덱스·제약 변경이나 기존 데이터 보정에 사용.
---

# DB 마이그레이션 추가

먼저 `src/main/resources/db/migration/AGENTS.md`를 읽는다.

1. 폴더를 정한다: `src/main/resources/db/migration/<module>/`. 새 모듈에 테이블을 도입하면 폴더를 만들고 `V1__init_<module>_module_table.sql`로 시작한다.
2. 최대 버전을 확인한다: `ls src/main/resources/db/migration/<module>/`. 새 파일은 `V<max+1>__<snake_case_설명>.sql`.
3. 적용된 파일은 수정하지 않는다. 이미 적용된 스키마나 데이터를 바꿔야 하면 새 파일에 변경 SQL을 작성한다.
4. 엔티티 매핑에 영향을 주면 엔티티(`<module>/internal/**`)도 바꾼다. 인덱스만 추가하거나 데이터를 보정하는 경우 엔티티 변경은 필수가 아니다. `ddl-auto: none`이라 엔티티와 SQL이 어긋나면 런타임에야 실패한다. 소프트 삭제 테이블은 `deleted_at DATETIME(6)`, 상태 컬럼은 `CHECK` 제약.
5. 시드가 참조하는 테이블(`deck_presets`, `note_types`, `card_templates`)이면 `db/seed/card/R__seed_default_data.sql`의 호환성을 확인하고 필요한 경우 수정한다. 기존 행의 값 변경은 별도 versioned 마이그레이션에 넣는다.
6. 검증: 해당 모듈의 Testcontainers 통합 테스트를 돌린다. 빈 DB에 처음부터 적용되므로 문법·순서 오류가 여기서 드러난다. 예: `./gradlew test --tests "com.whatever.caro.<module>.*Test"`. 기존 데이터의 변환이나 새 제약이 있으면 변경 전 데이터가 있는 DB에 추가 적용하는 경로도 검증한다.
7. 로컬 앱 기동 검증이 필요하면 `make run`으로 적용한다. `make reset`은 데이터 볼륨을 삭제하는 로컬 초기화이므로 마이그레이션 롤백으로 사용하지 않는다. 복구는 변경 내용에 맞게 추가 마이그레이션이나 백업 복원 등으로 계획한다.

## 완료 조건
- 필요한 새 `V` 파일 추가, 적용된 `V` 파일 변경 없음
- 매핑에 영향이 있으면 엔티티 반영, 통합 테스트 통과
- 기존 데이터에 영향이 있으면 추가 적용 검증, PR 설명에 복구 방안과 데이터 복구의 제약 기재
