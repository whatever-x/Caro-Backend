---
name: add-domain-event
description: 이벤트를 정의·발행하거나 리스너(`@ApplicationModuleListener`, `@EventListener`)를 추가·변경·삭제할 때의 절차와 완료 조건을 제공한다. 같은 모듈 안 이벤트를 포함한 후속 처리 추가, 이벤트 클래스·필드나 리스너 이름 변경에도 사용.
---

# 도메인 이벤트 추가

먼저 발행하는 모듈과 리스너를 둘 모듈의 AGENTS.md를 읽는다.

1. `origin/develop`에 있는 이벤트나 리스너를 바꾸거나 지우려면 바꾸기 전에 사용자에게 확인받는다. 그 전에 저장돼 재전달을 기다리는 발행이 아래처럼 처리되지 않는다.
   - 이벤트 클래스의 이름·패키지를 바꾸면, 옛 이름이 `event_type`에 남은 FAILED 행 하나 때문에 재전달 조회가 예외를 던져 모든 이벤트의 FAILED 발행 재전달이 멈춘다.
     이 상태는 그 행을 지우거나 `event_type`을 새 이름으로 고칠 때까지 계속된다.
   - 필드 이름을 바꾸거나 기본값 없는 필드를 추가하면, 저장된 JSON을 역직렬화하지 못해 그 실행의 뒤쪽 재전달이 중단되고 그 행은 RESUBMITTED로 남아 다시 전달되지 않는다. 새 필드에는 기본값을 둔다.
   - 리스너의 클래스·패키지·메서드 이름이나 파라미터 타입을 바꾸거나 리스너를 지우면, 이전 리스너로 저장된 발행은 `Listener ... not found!` 오류 로그만 남기고 재전달되지 않는다.
   - PR 설명에 운영 DB의 `event_publication`에서 그 이벤트(`event_type`)나 리스너(`listener_id`)의 `completion_date IS NULL`인 행을 세는 `SELECT`를 적는다.
2. 새 이벤트는 `data class`로 만들고 필드에는 id, 수치, 문자열, `java.time` 타입처럼 JSON으로 직렬화되는 값만 둔다. 엔티티를 넣지 않는다.
   발행된 이벤트는 리스너마다 JSON으로 `event_publication`에 저장되고, 재전달 때 리스너는 DB의 현재 행이 아니라 그 JSON으로 만든 객체를 받는다.
3. 새 이벤트를 같은 모듈에서만 받으면 그 모듈의 `internal/`에(`DeckCreatedEvent`), 다른 모듈이 받으면 루트 AGENTS.md 규약의 공개 위치에 둔다. 기존 이벤트의 패키지는 옮기지 않는다(1단계).
4. 발행은 `@Transactional` 메서드 안에서 `ApplicationEventPublisher.publishEvent`로 한다. 트랜잭션 밖에서 발행하면 리스너가 호출되지 않고, 남은 PUBLISHED 행은 `EventResubmitScheduler`도 재전달하지 않는다.
5. 리스너는 받는 모듈의 `internal/`에 두고 메서드에 `@ApplicationModuleListener`를 붙인다. `@EventListener`는 발행 트랜잭션 안에서 실행돼 리스너의 예외가 발행 요청을 롤백한다.
   `@ApplicationModuleListener`는 커밋 뒤 다른 스레드에서 실행돼 예외가 발행 요청의 응답에 반영되지 않으므로, 요청을 거절해야 하는 검증은 발행 전에 서비스에서 한다.
   이미 발행되고 있는 이벤트에 리스너를 새로 붙이면 배포 전에 발행된 이벤트는 그 리스너에 전달되지 않는다. 기존 데이터에도 적용해야 하면 사용자에게 확인받는다.
6. 같은 모듈 안 리스너도 `event_publication`에 기록돼 재전달되므로, 리스너는 같은 이벤트를 두 번 받아도 결과가 한 번 처리한 것과 같게 만든다.
   - 리스너가 만드는 행은 존재 확인으로 건너뛰고(`onCardsCreated`), 바꾸는 값은 이미 바뀐 행이면 다시 바꾸지 않는다(`onCardDeleted`는 첫 `deletedAt`을 유지한다).
   - 그 테이블의 UNIQUE 제약에 `deleted_at`이 없으면 존재 확인도 soft delete된 행까지 조회한다. 활성 행만 조회하면 재전달 때 유니크 위반으로 다시 FAILED가 된다.
   - 푸시 발송처럼 외부로 전송돼 취소할 수 없는 처리는 재전달 때 다시 전송되므로, 중복 방지 방법을 사용자에게 확인받는다.
7. 테스트는 `src/test/kotlin/com/whatever/caro/AGENTS.md`의 이벤트 리스너 테스트 항목을 따른다.
   - 리스너 통합 테스트(`@CaroModuleTest`)에 같은 이벤트를 두 번 발행하는 케이스를 넣는다(`CardLearningStateEventListenerTest`의 "이벤트가 여러번 들어와 재수행 되어도 card id 개수만큼만 DB에 생성된다").
   - 6단계에서 soft delete된 행까지 조회했으면 그 행이 있을 때의 케이스를 넣는다(같은 클래스의 "cardId가 soft-delete된 상태여도 유니크 위반 없이 나머지 cardId만 생성한다").
8. `./gradlew test --tests "com.whatever.caro.<리스너 모듈>.*"`로 검증한다. 모듈 간 이벤트면 `com.whatever.caro.ModularityTests`와 발행 모듈의 테스트를, 발행 모듈 AGENTS.md에 `--tests` 대상이 있으면 그것도 넣는다.
   끝나면 `make check`를 통과시킨다.

## 완료 조건
- 발행하는 메서드나 그 메서드를 부르는 메서드에 `@Transactional`이 있다. 리스너 테스트는 테스트 안에서 트랜잭션을 열고 발행하므로 이 누락을 잡지 못한다
- 리스너 통합 테스트에 같은 이벤트를 두 번 받는 케이스가 있고, 8단계 테스트와 `make check`를 통과한다
- 1단계에 해당하면 사용자 확인 결과와 미완료 발행을 세는 `SELECT`를 PR 설명에 적었다
- PR 설명에 멱등을 어기면 실패할 테스트 이름과 발행 메서드의 트랜잭션 경계(클래스·메서드 이름)를 적는다
