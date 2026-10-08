# withdrawal

- 파기 로직은 `internal/WithdrawnUserPurgeService.kt`(유저 1명 파기)와 `internal/WithdrawnUserPurgeSweeper.kt`(대상 조회·배치 루프) 두 파일에만 둔다. 삭제 SQL은 이 모듈 대신 각 모듈의 `internal` 리포지토리에 두고 여기서는 `*Api`만 부른다.
- 탈퇴 요청 자체는 `auth`의 `AuthService.withdrawUser`가 처리한다(refresh token 삭제, 블랙리스트 등록, `userApi.deleteMe`의 soft delete). 탈퇴 즉시 해야 하는 처리는 이 모듈 대신 거기에 넣는다.
- 새 파기 경로는 Modulith 이벤트 대신 `purge` 안의 동기 `*Api` 삭제 호출로 붙인다. 유저 1명이 한 트랜잭션이라야 중간 실패 시 그 유저만 롤백되고 다음 스케줄에 통째로 재시도된다.
- 트랜잭션 경계는 `WithdrawnUserPurgeService.purge`에만 둔다.
  sweeper에 `@Transactional`을 붙이면 한 유저의 실패가 배치 전체를 되돌리고, 두 클래스를 합치면 self-invocation이라 `purge`의 `@Transactional`이 무시되고 각 `*Api`가 자기 트랜잭션으로 커밋해, 실패한 유저의 데이터가 일부만 지워진다.
  두 경우 모두 `WithdrawnUserPurgeServiceTest`는 통과하므로 테스트로 확인되지 않는다.
- `purge`에서 `cardApi` 삭제를 `deckApi`보다 먼저 호출한다. `cards.deck_id`가 FK `fk_card_deck`으로 `decks`를 참조해 순서를 뒤집으면 제약 위반으로 파기가 통째로 실패한다.
- 각 `*Api`의 삭제 메서드는 `user_id`로 범위를 좁힌 hard delete로 쓴다. `note_types`·`card_templates`와 `deck_presets` 중 `user_id`가 NULL인 시스템 프리셋은 공유 리소스라, 함께 지우면 남은 유저의 `cards`·`decks`가 FK로 참조하고 있어 FK 위반으로 파기가 실패한다.
- 새 모듈이 `user_id` 데이터를 가지면 `purge`에 그 모듈 `*Api`의 삭제 호출을 추가하고 `ModuleMetadata.kt`의 `allowedDependencies`에 그 모듈을 선언한다. 기존 모듈에 `user_id` 테이블이 늘면 그 모듈의 `deleteAllByUserId`에 추가한다.
- `purge`에 탈퇴 여부 검사를 더하는 대신 `userApi.findWithdrawnUserIds`가 고른 id만 넘긴다. `purge`는 검사 없이 하드 삭제하고, 멱등 테스트가 탈퇴하지 않은 유저 id로 `purge`를 직접 부르므로 검사를 넣으면 그 테스트가 깨진다.
- 유예 기간이나 대상 조건은 `user`의 `UserRepository.findWithdrawnUserIds` 쿼리에서 바꾸고, 방금 탈퇴한 유저의 파기를 기대하는 `WithdrawnUserPurgeServiceTest`의 sweeper 케이스도 같이 고친다.
- 파기 호출은 같은 행을 다시 또는 동시에 지워도 안전하게 유지한다. 스케줄 잠금이 없어 인스턴스가 여럿이면 같은 유저를 동시에 파기하고, 실패한 유저는 탈퇴 상태로 남아 다음 스케줄에 재시도된다.
- 실행 주기와 활성 여부는 `application.yaml`의 `app.schedule.cron.withdrawn-user-purge`에서 바꾼다. 키를 지우면 폴백 `-`가 적용돼 오류 없이 파기만 멈춘다.
  한 실행은 `BATCH_SIZE`만큼의 유저까지만 파기하므로, 실행 주기와 `BATCH_SIZE`가 하루에 파기할 수 있는 유저 수를 정한다.
- 파기 대상 테이블을 늘리면 `WithdrawnUserPurgeServiceTest`의 seed, 존재 검증, `afterEach` 정리 목록에 그 테이블을 추가한다. 전수 삭제·타 유저 보존·멱등을 고정하는 테스트는 이것뿐이다.
- sweeper의 루프나 대상 선택을 고치면 `WithdrawnUserPurgeSweeperUnitTest`를 돌린다. 한 유저 파기가 실패해도 나머지 유저를 계속 파기하는 것과 대상이 없으면 `purge`를 부르지 않는 것을 고정한다.
