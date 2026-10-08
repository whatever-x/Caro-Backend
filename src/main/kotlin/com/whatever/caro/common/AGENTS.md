# common

- 다른 모듈이 참조하면 안 되는 구현은 common 대신 그 기능을 쓰는 모듈의 `internal/`에 둔다. common은 `Type.OPEN`이라 하위 패키지까지 전부 공개 타입으로 분류돼, 다른 모듈이 common의 `internal/` 패키지를 import해도 `ModularityTests`가 통과한다.
- 다른 모듈의 타입이 필요한 코드는 common 밖에서 구현한다. OPEN 모듈은 `ModularityTests`의 순환 검사에서 제외돼, common이 다른 모듈의 공개 타입을 참조해 생기는 순환은 테스트 실패로 나타나지 않는다.
  common에서 다른 모듈의 `internal/`을 참조하면 `ModularityTests`가 실패한다.
- 기존 `*ErrorCode`에 코드를 더할 때는 그 enum의 접두사와 최대 번호 + 1을 쓰고, 새 `*ErrorCode`에는 다른 enum이 쓰지 않는 접두사를 쓴다. `CommonErrorCode`는 특정 모듈에 속하지 않는 오류(프레임워크 예외, 멱등성, 처리하지 못한 예외의 C999)에만 쓴다.
  코드 중복을 검사하는 테스트가 없어 코드가 겹쳐도 빌드와 테스트가 통과하고, 응답 `error.code`로 분기하는 클라이언트가 그 오류를 같은 코드의 다른 오류로 처리한다.
  겹침은 `grep -rhoE --include='*ErrorCode.kt' '"[A-Z]+[0-9]{3}"' src/main/kotlin | sort | uniq -d`의 출력이 비어 있는지로 확인한다.
- 에러 코드의 `messageKey`는 `messages.properties`(영어, ko 외 모든 로케일에 쓰인다)와 `messages_ko.properties` 양쪽에 넣고, `{0}`을 쓴 문구는 던지는 쪽에서 `BusinessException`의 `args`로 채운다.
  이 규칙은 `GlobalExceptionHandler`가 만드는 응답에 적용된다. `args`를 빠뜨리면 `{0}`이 치환되지 않은 채 나가고, `messages_ko.properties`에 키가 없으면 한국어 요청이 영어 문구를, `messages.properties`에 없으면 ko 외 요청이 enum의 한국어 기본 문구를 받는다.
  `BusinessException`의 `message`는 로그에만 쓰이므로 사용자에게 보일 문구는 메시지 파일에서 바꾼다.
- 프레임워크 예외를 의도한 상태 코드로 응답하려면 `GlobalExceptionHandler`에 핸들러를 추가하고 메시지는 `resolveMessage`로 만든다. 처리하는 핸들러가 없는 예외는 `handleUnexpected`가 C999 500으로 바꾼다.
  보안 필터 체인에서 던진 예외는 DispatcherServlet 밖이라 이 클래스가 받지 않는다. 새 핸들러에서 `ApiResponse.fail`에 문자열을 직접 넘기면 `Accept-Language`와 무관하게 그 문자열이 응답 문구가 된다.
- `@Idempotent`는 클라이언트가 재시도해도 한 번만 처리돼야 하는 쓰기 엔드포인트(주로 POST)에 붙인다. GET처럼 원래 멱등인 요청에 붙이면, `IdempotencyInterceptor`가 HTTP 메서드를 검사하지 않아 같은 키의 조회가 `response-ttl` 동안 처음 결과를 받는다.
  인증이 필요한 경로에만 쓸 수 있다. `IdempotencyInterceptor`는 헤더 없음(C007), `UUID.fromString` 실패(C008), `SecurityUtil.currentUser()` 실패(A003 401) 순으로 검사하고 userId를 Redis 키 접두어로 쓴다.
  이 순서를 바꾸면 헤더가 없거나 형식이 틀린 미인증 요청의 응답이 400에서 401로 바뀌므로 테스트를 함께 추가한다. `IdempotencyInterceptorUnitTest`는 인증된 요청만 다뤄 이 순서를 검사하지 않는다.
- `@Idempotent` 엔드포인트를 고친 뒤 로컬에서 확인할 때는 새 `Idempotency-Key`를 쓴다. 같은 사용자·키의 요청은 `response-ttl` 동안 처음 응답(상태 코드·`Content-Type`·본문)을 그대로 받아 수정 전 응답이 나온다.
  method·path·query·body가 처음과 다르면 C009 409가 된다. 4xx 응답도 저장되며 5xx는 `@Idempotent(cacheInternalErrorResponse = true)`일 때만 저장된다.
- `RequestResponseCachingFilter`는 auth `SecurityConfig`의 `addFilterAfter<JwtAuthenticationFilter>`로 보안 체인 안에서만 실행되고, `SecurityFilterRegistrationConfig`가 container 등록을 비활성화한다.
  필터 위치나 등록 방식을 바꾸면 `addFilterAfter` 호출과 `SecurityFilterRegistrationConfig`를 같이 고친다.
  이 필터를 거치지 않은 요청은 인증된 `@Idempotent` 요청이어도 `IdempotencyInterceptor`의 `IllegalStateException`으로 C999 500이 되고, 필터 등록을 검사하는 테스트는 없다.
- `WebMvcConfig.configureApiVersioning`의 기본 버전 `1.0`을 올리기 전에 사용자에게 확인받는다. `API-Version` 헤더 없는 요청이 새 기본 버전으로 해석돼 `version = "1.0"`으로만 매핑된 엔드포인트가 전부 400 C013이 된다.
  이 변경을 실패로 잡는 테스트는 없다(WebMvc 테스트는 모두 헤더를 명시한다). 새 버전은 매핑에 `version`만 선언하면 지원 버전으로 자동 등록되므로 `addSupportedVersions`는 호출하지 않아도 된다.
- `OpenApiConfig`에 버전 그룹을 추가하면 `versionMethodFilter`에 넘기는 문자열을 매핑의 `version` 표기(`"1.0"`, `"2.0"`)와 똑같이 쓴다. 필터가 문자열 동등 비교라 표기가 다른 매핑은 `version-N` 그룹 문서에 포함되지 않고 `all-version`에만 나온다.
  `"2"`·`"2.0.0"`(라우팅에서는 2.0과 같은 버전이다), `"1.0+"` 같은 baseline 표기, `version` 생략이 여기에 해당한다.
  그룹의 displayName을 바꾸면 `application.yaml`의 `springdoc.swagger-ui.urls-primary-name`도 같이 바꾼다. 다르면 오류 없이 Swagger UI가 그룹 목록의 첫 항목을 기본 문서로 표시한다.
- `OpenApiConfig.apiGroupAll`의 Kotlin `@Deprecated` 제외 조건은 유지한다. 같은 path+method에 버전이 둘인 엔드포인트(예: `GET /decks/{deckId}/cards`의 `CardController.getCardsByDeck`)에서 구버전을 `all-version` 문서에서 빼는 조건이다.
  지우면 두 operation이 하나로 합쳐져 요약·deprecated 표시·`API-Version` 기본값이 구버전 것이 되고 200 응답 스키마가 두 버전의 `oneOf`가 된다. `@Operation(deprecated = true)`는 이 조건에 해당하지 않는다.
- `EventResubmitScheduler`의 전달 시도 횟수 상한(`completionAttempts < 3`)을 바꾸려면 사용자에게 확인받는다. `completionAttempts`는 최초 전달을 1로 세고 재전달마다 1씩 늘어나므로, 재전달 횟수의 최댓값은 상한보다 1 작다.
  재전달이 모두 실패해 상한에 도달한 발행은 `event_publication`에 FAILED로 남고, `republish-outstanding-events-on-restart`가 `off`라 재기동해도 다시 전달되지 않는다. 경계는 `EventResubmitSchedulerTest`가 검사한다.
- `GlobalExceptionHandler`를 바꾸면 바뀐 응답의 케이스를 그 응답을 내는 컨트롤러의 `*WebMvcTest`에 추가하고 `./gradlew test --tests "*WebMvcTest"`로 돌린다.
  단언 방식은 `src/test/kotlin/com/whatever/caro/AGENTS.md`의 예외 응답 항목을 따른다.
  `common.*`에는 `GlobalExceptionHandler`를 검사하는 테스트가 없고, 에러 응답 케이스는 `CardControllerWebMvcTest`·`DeckControllerWebMvcTest`·`DeckBFFControllerWebMvcTest`에만 있다.
  메시지 키 해석이나 `Accept-Language` 처리를 바꾸면 문구 검증 케이스도 추가한다. 이를 검사하는 테스트가 없어 메시지 키나 로케일 처리가 틀려도 테스트가 통과한다.
