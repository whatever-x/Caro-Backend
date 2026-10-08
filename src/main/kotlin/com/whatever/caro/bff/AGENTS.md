# bff

- 도메인 규칙은 `card`와 `study`에 두고, bff는 두 모듈의 `*Api` 결과를 합쳐 응답 형태로 바꾸는 일(정렬, 배지 판정, 진행도 합계, 큐 순서대로 카드 내용 붙이기)을 한다. 세션 생성 같은 상태 변경은 `studyApi`를 호출해 처리한다.
- 조합 API의 서비스는 `internal/`에 둔다. 서비스는 `*Api` 결과를 `internal/`의 뷰 모델로 바꿔 반환하고, `*Response`로의 변환은 컨트롤러(`internal/web/`) 파일 안의 `toResponse()` 확장 함수가 한다.
  bff 컨트롤러의 Swagger `@Tag`는 도메인 모듈 컨트롤러와 같은 이름(`Deck`, `StudySession`)을 쓴다.
- `*Api` DTO에 없는 값을 응답에 담으려면 `card`·`study`의 `*Api` DTO(`CardContentDto`, `CardLearningStateDto`, `DeckInfoResponse`, `StudySessionDto` 등)에 필드를 먼저 추가한다.
  bff에는 엔티티도 리포지토리도 없고 다른 모듈의 리포지토리는 `internal/`이라 참조하면 `ModularityTests`가 실패하므로, 이미 받은 값으로 계산하는 값(배지, 진행도 합계)만 bff에서 만든다.
- HARD 배지는 `DeckPresetApi`가 준 `hardBadgeThreshold`로 판정하고, 학습 카드 순서는 `studyApi.getStudySessionCardQueue`가 준 큐 순서를 따른다.
  `hardBadgeThreshold` 대신 기본 프리셋의 값을 상수로 쓰면 `deck_presets`를 UPDATE 마이그레이션으로 바꾸거나 프리셋을 늘려도 배지 기준이 따라 바뀌지 않는다.
  `cardApi.getCardsByIds`의 Map은 DB 조회 순서(ORDER BY 없음)라서, Map 순서대로 카드를 꺼내면 큐 순서(신규 카드 먼저, 복습 카드는 `nextReviewDate` 순)와 다른 순서로 응답된다.
- 요청마다 `Instant`(`Clock` 빈)와 `Client-Timezone` 헤더의 `ZoneId`는 controller에서 한 번만 얻어 모든 `*Api` 호출에 같은 값을 넘긴다.
  세션 날짜는 시작 시각과 타임존으로 정해지므로, 호출마다 다시 얻어 현지 날짜가 바뀌면(`now`가 현지 자정을 넘기거나 타임존이 달라질 때) `getStudySessionCardQueue`가 `SessionExpiredException`(409, S002)으로 실패한다.
- 덱 진행도는 `studyApi.getTodaySummaries`에 deckId 집합을 한 번에 넘겨 구하고, 결과에 요청한 deckId가 모두 있다는 전제로 `getValue`를 호출한다. 덱마다 호출하면 쿼리 수가 덱 수에 비례해 늘어난다.
- 덱 소유권과 존재 검사는 `card`의 `DeckApi.getDeck`과 `CardApi.getCardContentsByDeck`이 `DeckForbiddenException`(403)과 `DeckNotFoundException`(404)으로 한다. `study`의 `getTodaySummaries`는 사용자 필터만 걸고 소유권 오류(403·404)를 던지지 않는다.
  그래서 `GET /decks/{deckId}`는 `deckApi.getDeck`을 `getTodaySummaries`보다 먼저 호출한다. `GET /decks/{deckId}/cards`는 `cardApi.getCardContentsByDeck`을 호출하기 전에 빈 목록을 반환하지 않는다. 먼저 반환하면 다른 사용자의 덱도 403 대신 200과 빈 목록으로 응답된다.
- `DeckBFFService`의 `CardLearningStatus.SUSPENDED` `error()`는 `study`가 SUSPENDED를 저장하지 않는다는 전제로 작성됐고, `DeckBFFServiceUnitTest`가 `IllegalStateException`이 나는지 검사한다.
  `study`가 SUSPENDED를 반환하기 시작하면 카드 한 장 때문에 `GET /decks/{deckId}/cards` 응답 전체가 500이 되므로, badge 매핑과 `CardLearningStateBadge`, 이 테스트 기대를 함께 바꾼다.
- `TodayStudySessionState.NotStarted`는 `getTodaySummaries`가 정상적으로 반환하는 값이므로 덱 진행도에서는 NOT_STARTED로 매핑한다. `NotStarted`에 대한 `error()`는 `StudyBFFService.startOrResumeDailyStudy`에만 둔다.
  `startOrResumeDailyStudySession`이 `NotStarted`를 반환하지 않는다는 전제는 `studyApi`를 mock한 `StudyBFFServiceUnitTest`가 검사하지 못하므로, `StudyService`의 반환 경로를 바꾸면 이 `error()`도 함께 고친다.
- `DailyStudyResponse`에 서브타입을 더하면 `@JsonSubTypes`와 `@Schema`의 `discriminatorMapping`에 항목을 추가한다. 두 곳은 컴파일러가 검사하지 않는다.
  `@JsonSubTypes`에서 빠지면 `type` 키에 `REST_DAY` 같은 enum 값 대신 `DailyStudyResponse$RestDay` 같은 클래스명이 들어가고, `discriminatorMapping`에서 빠지면 OpenAPI 문서의 discriminator mapping에 그 값이 없다.
- `internal/`의 `StudyCardItem`·`CardLearningStateBadge`와 `study`의 `TodaySummaryState`는 변환 없이 응답 JSON으로 직렬화된다(`DailyStudyResponse.cards`, `DeckCardResponse.badge`, `StudySessionProgressResponse.state`, study의 `GET /study-sessions/daily/summary`의 `type`).
  필드명이나 enum 상수를 바꾸거나 지우면 기존 클라이언트가 응답을 해석하지 못하는 하위 호환되지 않는 변경이므로, 사용자에게 먼저 확인받는다.
- 일일학습 세션 중복 생성은 두 곳에서 막는다. 같은 키의 재시도는 `@Idempotent`가, 키가 다른 동시 최초 생성은 `study`의 `uk_session_per_day`가 막는다.
  `@Idempotent`는 (사용자, 키) 단위라 키가 다른 동시 요청은 막지 못한다.
- bff 코드를 고친 뒤 `./gradlew test --tests "com.whatever.caro.bff.*"`로 좁게 돌린다. 배지·진행도 매핑은 `DeckBFFServiceUnitTest`, 정렬은 `CardSorterTest`, 큐 순서와 누락 카드는 `StudyBFFServiceUnitTest`, v2.0 요청의 `sortType` 바인딩은 `DeckBFFControllerWebMvcTest`가 검사한다.
  `GET /decks`, `POST /study-sessions/daily`, 헤더 없는 `GET /decks/{deckId}/cards`의 v1.0 라우팅은 동작을 검사하는 테스트가 없으니 바꿨으면 테스트를 함께 추가하고, `*Api` DTO를 바꿨으면 `make check`까지 돌린다.
