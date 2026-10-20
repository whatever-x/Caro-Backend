# 기간별 학습 리포트 — 서버 설계

## 정본과 상태

이슈: https://github.com/whatever-x/Caro-Workflow/issues/12 (기존 Caro-Planning#6에서 이전)

공통 정책·필드 타입·UI·수용 조건의 정본은 [Caro-Mobile 기능 스펙](https://github.com/whatever-x/Caro-Mobile/blob/docs/learning-report-spec/feature/learning/docs/2026-10-16-learning-report-spec.md)다. 이 문서는 서버 조회 책임·집계·삭제·검증 상세만 보완한다. 공유 브랜치는 Mobile `docs/learning-report-spec`, Backend `chore/learning-report-spec`이며 API 계약은 구현 전 제안이다.

상태는 Step3 초안이다. 삭제 이력 처리는 사용자 답변 대기다. 제품 코드·DDL·기존 평가 동작은 변경하지 않았다.

기준: Backend `develop` `8b9b8418e7e7518860ba8e6a96fd7de7a748cbcb`, Mobile `develop` `36397502b3086acb10883c0c2c2fdbe817c5894f`, Planning `main` `43fb43c6f3fa9e60e7263bd81fcd8831187a1a20`. 2026-10-16에 원격 HEAD와 일치를 확인했고 2026-10-20 재개 때도 같은 SHA다. workflow 미러는 FE·BE가 뒤처져 직접 원본 clone을 사용했다.

## 기존 저장·조회와 차이

- `study/internal/studysession/ReviewLog.kt`는 userId/cardId/session/rating/reviewType/timeMs/createdAt/복습 전후 값 저장. 본문 스냅샷은 없다.
- `StudySession.kt`의 sessionDate는 시작 당시 timezone과 dayCutoffHour로 계산해 저장한다. 리포트 날짜도 이 값이다.
- `EvaluationService.evaluate`가 유효한 평가 로그와 카운터를 같은 트랜잭션에서 저장한다. 완료되지 않은 세션의 로그도 기록이다.
- `ReviewLogRepository.findAllByStudySessionId`는 단일 세션 조회다. 기간별 전체 로그를 이 메서드 반복 호출로 읽지 않는다.
- `StudyController`에는 특정 덱의 오늘 요약만 있다. 리포트 5개 경로는 신규 제안이다.
- `CardLearningStateEventListener`는 카드 삭제 시 학습 상태를 soft delete하고 목표를 재계산한다. 평가 로그를 지우는 경로는 이 리스너에서 확인되지 않았다.
- `CardApi.getCardsByIds(userId,ids)`는 활성 카드의 현재 내용을 일괄 반환한다. 삭제/누락 카드는 결과에 없으므로 리포트에서는 placeholder로 처리할 수 있다. 누락만으로 타인 카드의 삭제 여부를 추론하지 않고 본인 로그의 cardId만 조회한다.
- `DeckApi.getDecks/getDeck`은 활성 덱만 다룬다. 삭제 덱의 이름을 조회하기 위해 기존 API의 필터를 느슨하게 하지 않는다.
- `DeckService.deleteDeck`은 덱만 soft delete하고 `DeckDeletedEvent` listener가 없다(`card/AGENTS.md`). 삭제 덱의 카드는 `deletedAt`이 null로 남으므로 `getCardsByIds`가 그 본문을 반환할 수 있다. 리포트는 카드 활성 여부와 별도로 덱 삭제 여부를 확인해 본문을 숨겨야 한다.
- `StudyApi.deleteAllByUserId`와 withdrawal의 파기 순서는 유지한다. 새 로그 사본·통계 테이블을 만들지 않아 파기 경로를 추가할 필요가 없는 안이다.

## 모듈 경계

`study` 안에 보고용 읽기 service와 repository projection을 둔다. `StudyApi`에 필요한 보고 DTO와 조회 메서드만 추가한다. `bff`는 이 API와 `CardApi`·`DeckApi`를 조합하고 응답 DTO 변환을 controller에 둔다. bff가 study/card 내부 repository나 엔티티를 import하지 않는다. 별도 report Modulith 모듈은 만들지 않는다.

카드명·덱명은 study 테이블에 복제하지 않는다. 페이지에 필요한 cardId/deckId만 일괄 조회한다. 덱 옵션은 card의 사용자 제한 공개 API로 활성 덱과 삭제 여부를 읽고, 이력만 남은 삭제 덱은 본인 세션의 증거로 표시한다. 삭제된 이름은 반환하지 않는다. 덱별 호출이나 카드별 호출로 N+1을 만들지 않는다.

## 신규 GET 계약

정확한 필드 계약은 Mobile 정본의 ‘신규 API 계약’을 따른다.

1. `/study-reports/summary`: 선택 범위 Counts, 범위별 Bucket, 실제 확인되는 사용자 휴식일. 전체(deckId 생략)에서만 lifetime Counts를 포함하며 개별 덱 응답은 해당 필드를 생략한다.
2. `/study-reports/decks`: 소유 덱 선택용 페이지. deckId 내림차순. 삭제 상태 변경만으로 순서를 바꾸지 않는다. 목록 맨 위 ‘전체’는 FE가 고정 삽입하고 선택 시 deckId를 생략한다. 서버에서 가짜 deckId를 만들지 않는다.
3. `/study-reports/sessions`: 세션별 기록 페이지.
4. `/study-reports/cards`: 필수 deckId, 선택 기간 안 카드별 마지막 평가·평가 수, lastRating 필터.
5. `/study-reports/cards/{cardId}/reviews`: 필수 deckId와 카드의 평가 이력 페이지.

모두 명시적 매핑 `version = "1.0"`, Bearer 인증, Client-Timezone, 기존 ApiResponse. userId는 SecurityUtil에서만 얻는다. read-only 트랜잭션 사용, 평가·streak 동기화·idempotency interceptor를 붙이지 않는다.

날짜 입력은 양쪽 생략=전체 기간, 한쪽만 입력=400. 기본 7일은 FE 명시 범위로 전달한다. `Clock`의 현재 시각은 controller에서 한 번만 얻어 검증과 API 조합에 전달한다. 시간대·날짜·cursor의 바인딩 오류가 기존 GlobalExceptionHandler에서 의도한 400으로 처리되는지 WebMvc 테스트로 검증한다. 미처리 예외를 500으로 남기지 않는다.

## 집계 원칙과 쿼리

기본 조건은 `rl.userId=:userId AND ss.userId=:userId AND rl.studySession=ss AND ss.studyType=DAILY`, deckId 지정 시 `ss.deckId=:deckId`, 날짜 지정 시 `ss.sessionDate BETWEEN :from AND :to`다. 삭제 정책이 유지로 확정되면 현재 카드/덱의 deletedAt을 집계 조건에 넣지 않는다. 모든 세션·카드를 읽어 Kotlin에서 집계하는 구현을 피한다.

- Counts는 DB count/count distinct/조건부 합계로 구한다. `count(distinct rl.cardId)`와 `count(distinct ss.sessionDate)`는 선택 범위 전체에서 계산한다.
- 버킷은 DB의 학습일별 집계를 기반으로 최대 선택 구간만 반환하고, 긴 기간은 주/월 집계로 축약한다. 모든 원시 로그를 앱으로 보내지 않는다.
- 버킷들의 reviewCount 합과 summary.selected.reviewCount는 동일 조회 시점에서 같아야 한다. summary 내부 여러 조회는 동일 읽기 트랜잭션 스냅샷을 사용한다.
- lifetime은 전체(deckId 생략)에서만 집계·반환하고 날짜 조건은 제외한다. 개별 덱에서는 누적 집계와 필드를 모두 생략한다. 누적 학습일은 평가가 하나 이상인 학습일이며 streak 완료일 수와 다르다.
- 세션 목록은 session별 집계 projection을 `(sessionDate,sessionId)` 내림차순 keyset으로 읽는다. 평가 0개 세션은 목록에 없다.
- 카드 목록은 기간 내 cardId로 그룹화하고 `MAX(createdAt,id)` 순서로 마지막 평가를 선택한다. lastRating 조건은 그룹 안의 ‘아무 평가’가 아니라 마지막 평가 행에 적용한다. 구현은 MySQL 지원 버전에서 window function 또는 동등한 anti-join projection으로 검증한다. JPA에서 지원하지 않는 문법을 임의 JPQL로 쓰지 않는다.
- 카드 이력은 `(createdAt,id)` 내림차순. 같은 cardId라도 사용자·deckId·DAILY 조건을 반드시 유지한다.
- limit 20, 허용 1..100, `limit+1` 조회로 nextCursor 여부 확인. 평가 기반 목록의 cursor는 정렬 키·첫 페이지 maxReviewId·필터를 담되 검증 후 사용한다. 최초 maxReviewId 이하만 이후 페이지에서 조회한다. 덱 선택 목록은 deckId 정렬 키와 최초 maxDeckId를 사용한다. cursor 내용이 바뀌어도 userId는 바뀔 수 없다.
- 날짜별 빈 구간은 0으로 채운다. 과거 REST_DAY 기록이 없다는 사실을 미학습 또는 휴식이라고 단정하지 않는다. 덱별 REST_DAY 이력은 저장돼 있지 않아 제공하지 않는다.
- summary와 개별 목록을 여러 HTTP 요청에서 같은 스냅샷으로 유지하는 기능은 범위 밖이다. 새 평가 후 새로고침으로 갱신한다.

## 삭제·수정·권한

삭제 후 이력 유지안의 상세다. 사용자 답변이 다르면 이 절과 공통 AC08을 수정한다.

- 현재 소유의 활성 덱은 허용한다. 삭제 덱은 현재 사용자의 DAILY 세션으로 해당 deckId의 이력이 확인될 때만 허용한다. 타인의 현재 덱 또는 다른 사용자의 로그로 허용하면 안 된다.
- 카드 이력은 본인 로그+요청 deckId에 해당하는 cardId만 허용한다. 식별자만으로 카드 본문을 읽지 않는다. 삭제 카드와 삭제 덱에 속한 카드의 본문은 null, `deleted=true`다. 덱 삭제 여부는 card 모듈의 사용자 제한 공개 API로 같은 페이지의 deckId를 일괄 확인한다.
- 기존 덱 API는 타인 덱에 403, 없는 덱에 404를 줄 수 있다. 새 리포트 API는 접근 불가·존재 안 함을 404로 통일하는 제안이며 기존 계약을 수정하지 않는다.
- 덱이나 카드 수정 시 현재 내용만 바뀐다. 과거 수치와 평가 자체는 바꾸지 않는다. ‘평가 당시 내용’ 표시는 하지 않는다.
- 카드 삭제로 목표 카운터가 재계산돼도 과거 보고 수치는 ReviewLog 기준으로 유지한다. 이름/본문의 복구 경로를 만들지 않는다.
- 일반 삭제와 계정 탈퇴 개인정보 파기는 구분한다. 후자는 기존 사용자 데이터 hard delete 경로에 따라 로그·세션을 제거한다.

## 스키마와 성능 검증

현재 study migration 최대는 V8이다. Step4에서는 다시 확인한다. 저장 컬럼을 새로 만들 필요는 없는 안이다. 보고 조회의 실제 EXPLAIN과 데이터 규모를 확인한 후에만 필요한 인덱스를 새 migration으로 추가한다. 후보는 sessions `(user_id,study_type,session_date,id)`와 review_logs `(user_id,study_session_id,id)`, 카드별 이력용 `(user_id,card_id,created_at,id)`다. 기존 고유 인덱스·FK 인덱스와 중복 여부를 검증하고 모두 일괄 추가하지 않는다.

임의 응답시간 SLA나 실제 계정의 기록 수를 가정하지 않는다. Step4에서 기록 규모·실행계획·쿼리 수와 P95를 측정해 근거를 PR에 남긴다. 월/주 집계를 위한 캐시·일별 요약 테이블은 우선 추가하지 않는다.

## 검증 계획

- repository: 같은 카드를 여러 날·여러 덱으로 평가한 표본, 기간 양끝, 미래/역전 날짜, 동일 시각 평가, 0건·중단 세션·FULL_DECK 제외, 삭제 전후 수치 보존.
- 통합: userId가 다른 세션/로그/카드·덱의 접근 차단, 변조 cursor·타인 식별자 404, 계정 탈퇴 파기 회귀.
- pagination: 0/1/20/21건, 같은 정렬 키, 페이지 사이 새 평가 추가, 필터 변경 cursor 거절, 추가 페이지 실패 재시도.
- WebMvc: 명시 API-Version/기본 버전 유지, 헤더·date·enum·limit 검증, 인증 오류와 기존 ApiResponse·지역화 메시지.
- 집계 불변식: ratings 합계=평가 횟수, buckets 합계=기간 평가 횟수, 전체 선택에서 누적>=선택 범위(같은 시점), 개별 덱 응답에 lifetime 없음, 고유 카드<=평가 횟수.
- `./gradlew test --tests "com.whatever.caro.study.*" --tests "com.whatever.caro.bff.*" --tests "com.whatever.caro.ModularityTests"` 및 `make check`. migration/엔티티 변경 시 withdrawal 테스트도 포함.

이번 단계에서 실행한 것은 원본/참조 확인과 설계 문서·시안 검사다. 2026-10-20 재개 때 카드·덱 삭제 경로를 다시 읽고 삭제 덱 카드의 본문 숨김을 추가했다. 위 제품 테스트·EXPLAIN·부하 검증은 실행하지 않았다.
