---
name: add-scheduled-job
description: 주기 실행 작업(`@Scheduled`)을 새로 만들거나 기존 작업의 실행 주기·배치 크기·대상 조건을 바꾸거나 끌 때의 절차와 완료 조건을 제공한다. 스케줄러·sweeper·배치 작업·리마인더처럼 매일·매시간 도는 작업 추가와 cron 변경에 사용.
---

# 주기 작업 추가

먼저 `src/main/resources/AGENTS.md`의 `app.schedule.cron.*` 항목을 읽고, 1단계에서 모듈을 정하면 그 모듈의 AGENTS.md를 읽는다.
`EventResubmitScheduler`의 주기·재전달 조건은 이 절차 대신 `common/AGENTS.md`를 따른다.

1. 작업 클래스는 대상 데이터를 가진 모듈의 `internal/`에 둔다. 여러 모듈의 데이터를 함께 처리하면 둘 모듈을 사용자에게 확인받는다.
   다른 모듈의 데이터는 그 모듈의 `*Api`로 조회·변경한다.
2. 실행 주기는 `@Scheduled(cron = $$"${app.schedule.cron.<이름>:-}")`로 받고, `application.yaml`의 `app.schedule.cron` 아래에 같은 이름의 키를 추가한다.
   키가 없거나 이름이 다르면 폴백 `-`가 적용돼 오류 없이 그 스케줄만 등록되지 않는다. 작업을 끄는 값은 `src/main/resources/AGENTS.md`를 따른다.
   새 작업은 `fixedDelay`·`fixedRate` 숫자 대신 cron 키를 쓴다. 코드에 숫자로 넣으면 yaml로 작업을 끄거나 주기를 바꿀 수 없다.
   cron 값은 따옴표로 감싸고 UTC 시각으로 쓰며 `@Scheduled`에 `zone`을 지정하지 않는다.
   `*`로 시작하는 값은 따옴표가 없으면 YAML alias로 읽혀 기동이 실패하고, 앱의 기본 시간대가 UTC라 KST 기준으로 쓰면 9시간 어긋난 시각에 실행된다.
3. 스케줄 메서드는 `Clock`으로 얻은 시각을 인자로 받는 메서드에 넘긴다. 스케줄 메서드 안에서 시각을 바로 쓰면 통합 테스트가 날짜 경계를 고정하려고 `Clock` 빈을 바꿔야 해 테스트 컨텍스트가 하나 더 뜬다.
   사용자의 현지 날짜·시각이 기준이면 타임존과 day cutoff를 어디서 읽을지 사용자에게 확인받는다.
   스케줄 실행에는 요청의 `Client-Timezone`이 없어 루트 AGENTS.md의 학습일 규약을 그대로 쓸 수 없고, 서버 UTC 날짜로 계산하면 실행 시각에 날짜가 UTC와 다른 사용자는 하루 어긋난 날짜로 처리된다.
4. 항목을 하나씩 처리하는 작업은 항목 하나를 처리하는 메서드를 다른 빈에 두고 그 메서드에만 `@Transactional`을 붙인다(`WithdrawnUserPurgeSweeper`와 `WithdrawnUserPurgeService`).
   스케줄 메서드에 붙이면 한 항목의 실패가 배치 전체를 롤백하고, 같은 클래스 안에서 호출하면 self-invocation이라 `@Transactional`이 적용되지 않는다.
   항목별 예외는 `runCatching`으로 잡아 로그를 남기고 다음 항목으로 진행한다. 잡지 않으면 남은 항목은 다음 실행까지 처리되지 않는다.
   작업이 이벤트를 발행하면 항목 처리 메서드의 트랜잭션 안에서 발행한다. 트랜잭션 밖에서 발행하면 리스너가 호출되지 않는다.
5. 한 실행에서 처리할 수는 `BATCH_SIZE` 상수로 제한한다. 제한이 없으면 쌓인 대상 전체를 한 번에 조회해 실행 시간과 메모리 사용이 대상 수에 비례해 늘어난다.
   처리에 실패한 항목이 다음 실행의 조회 결과에 다시 들어가 `BATCH_SIZE`를 채우면 나머지 항목은 처리되지 않으므로, 실패한 항목을 제외하거나 뒤로 정렬하는 조회 조건을 둔다.
6. 같은 작업이 여러 인스턴스에서 동시에 실행될 수 있다고 전제하고, 같은 항목을 두 번 또는 동시에 처리해도 결과가 같게 만든다.
   푸시 발송처럼 외부로 전송돼 취소할 수 없는 처리는 동시에 실행되면 두 번 전송되므로, 중복 방지 방법을 사용자에게 확인받는다.
7. 테스트는 `src/test/kotlin/com/whatever/caro/AGENTS.md`를 따르고 스케줄 메서드나 3단계의 시각 인자 메서드를 직접 호출한다. cron 키와 실행 시각은 테스트 대신 완료 조건의 명령으로 확인한다.
   - 항목을 하나씩 처리하는 작업은 처리 빈을 mock한 단위 테스트로, 한 항목이 실패해도 나머지를 처리하는 것과 대상이 없으면 처리하지 않는 것을 단언한다(`WithdrawnUserPurgeSweeperUnitTest`).
   - 대상 조회 조건이나 항목 처리를 바꾸면 `@CaroModuleTest` 통합 테스트에 실패한 항목이 제외되거나 뒤로 정렬되는 케이스를 넣는다.
   - 같은 항목을 두 번 처리해도 결과가 같은 케이스를 넣는다(`WithdrawnUserPurgeServiceTest`의 "이미 파기된 유저를 다시 파기해도 예외 없이 멱등하게 동작한다").
   - cron 값만 바꾸면 테스트를 추가하지 않는다.
8. `./gradlew test --tests "com.whatever.caro.<module>.*"`로 검증하고 `make check`를 통과시킨다.

## 완료 조건
- cron 키를 추가하거나 바꿨으면 아래 명령의 출력이 비어 있다
  `for k in $(grep -rhoE --include='*.kt' 'app\.schedule\.cron\.[a-z0-9-]+' src/main/kotlin | sed 's/.*cron\.//' | sort -u); do grep -q " $k:" src/main/resources/application.yaml || echo "$k"; done`
- 7단계에 해당하는 단위·통합 테스트가 있고, 8단계 테스트와 `make check`를 통과한다
- PR 설명에 실행 주기(UTC), 동시 실행 시의 결과, 사용자 확인 결과(1·3·6단계), 실패 격리·실패 항목 조회 조건·중복 처리를 어기면 실패할 테스트 이름을 적는다
