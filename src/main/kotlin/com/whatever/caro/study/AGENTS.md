# study

- 규칙은 `internal/SchedulingState.kt`(SM-2 순수 계산), `internal/studysession/StudySession.kt`(세션 날짜·목표·완료 판정), `internal/EvaluationService.kt`(평가 처리), `internal/StudyService.kt`(세션 생성·목표 재계산·큐 조회), 루트 패키지의 `StudyTargetPoolCalculator.kt`(오늘 학습 대상 수), `internal/cardlearningstate/CardLearningStateRepository.kt`(큐와 대상 수의 JPQL 조건), `internal/streak/StreakService.kt`(streak), `internal/event/`(카드·완료 이벤트 수신)에 나뉘어 있으니 고칠 규칙이 들어 있는 파일부터 읽는다.
- 이 모듈은 학습 상태·평가 기록(`ReviewLog`)·SM-2 스케줄링·일일 목표·streak을 맡고, 카드 내용은 `card`가, 화면 조합은 `bff`가 맡는다. `card`에서는 `card :: deck`·`card :: event`만 참조하므로(`ModuleMetadata.kt`), 카드 본문이 필요하면 허용 목록에 추가하지 말고 `bff`에서 합친다.
- 오늘 세션은 요청 `Client-Timezone` 기준으로 `sessionDate`가 어제~오늘인 행을 읽은 뒤, 세션에 저장된 `dayCutoffHour`로 `StudySession.isTodaySession`을 판정해 고른다(`StudyService.findTodaySession`). 같은 하한이 `getTodaySummaries`에도 있어 함께 바꾼다.
  cutoff가 0보다 크면 `sessionDate`가 어제인 세션이 오늘 세션일 수 있어 하한이 어제여야 한다. 하한을 오늘로 올리면 cutoff 이전 시각에 기존 세션을 찾지 못해 같은 `sessionDate`로 INSERT하다 `uk_session_per_day` 위반(500)이 난다.
  cutoff가 0이거나 요청 현지 시각이 cutoff 이후인 호출은 하한이 어제든 오늘이든 결과가 같아서, 하한을 오늘로 올려도 기존 테스트는 통과한다.
  결과가 달라지는 호출은 현지 시각이 cutoff 이전인 경우뿐이고, `findTodaySession`·`getTodaySummaries`의 조회 하한을 검사하는 테스트는 없다.
- 카드 상태는 NEW·REVIEW만 쓴다. SUSPENDED는 enum과 DB CHECK 제약에 이미 들어 있는 값이므로 지우지 않고, 이 상태로 전이하는 코드는 추가하지 않는다.
  `Sm2Params.leechThreshold`는 SUSPENDED용으로 남긴 값이라 계산에 쓰지 않는다. SUSPENDED 전이를 추가하려면 사용자에게 확인받고, `CardLearningState.toSchedulingState`와 `bff`의 `DeckBFFService`가 `SUSPENDED`에 내는 `error()`를 함께 바꾼다.
- 세션 상태는 ACTIVE에서만 바뀌고, 응답에서는 STOPPED도 `Completed`로 반환한다(`StudyService.kt`의 `toTodayState`).
  `StaleSessionSweeper`는 ACTIVE로 남은 세션 중 `sessionDate`가 서버 UTC 날짜보다 3일(`GRACE_DAYS`) 이상 이전인 것만 STOPPED로 바꾼다. 실행 시각(04:00 UTC)에 클라이언트의 오늘은 UTC 날짜보다 최대 1일 늦고 cutoff를 17시 이상으로 쓰면 최대 2일 늦으므로, 유예를 그 일수 이하로 줄이면 아직 오늘인 세션이 STOPPED가 되어 평가가 `SESSION_NOT_ACTIVE`(409)로 거부된다.
- 세션 완료 조건(NEW와 REVIEW 각각 studied가 goal 이상)은 `StudySession.completeIfGoalAchieved` 한 곳에서만 판정한다. 이를 부르는 곳은 평가(`EvaluationService.evaluate`)와 목표 재계산(`StudyService.recalcGoals`, 세션 재개와 카드 삭제에서 호출) 두 곳이므로, 조건을 바꾸면 `StudySessionTest`, `EvaluationServiceTest`, `StudyServiceTest`, `StudyServiceOnCardDeletionTest`를 돌린다.
- 카드 이벤트 리스너는 생성 때 soft delete된 행까지 조회하는 `findAllByUserIdAndCardIdIn`으로 이미 있는 cardId를 제외하고 저장하고, 삭제 때 학습 상태 soft delete와 세션 목표 재계산을 함께 한다.
  `uk_ls_card(card_id)`에는 `deleted_at`이 없어 soft delete된 행에도 적용되므로, 활성 행만 조회하도록 바꾸면 삭제 처리 뒤에 생성 이벤트가 재전달될 때 유니크 위반이 난다(`CardLearningStateEventListenerTest`).
  `recalculateGoals`는 기존 goal을 상한으로 두고 줄이기만 하므로, 그 직후 `completeIfGoalAchieved`를 부르지 않으면 goal을 모두 채운 세션이 ACTIVE로 남는다. 이때 `StudyBFFService`는 빈 큐를 Completed로 응답하지만 덱 목록 진행 상태는 IN_PROGRESS이고, 같은 날 평가 요청이 없으면 `sessionDate`로부터 3일 뒤 `StaleSessionSweeper`가 STOPPED로 바꿀 때까지 ACTIVE로 남는다(`StudyServiceOnCardDeletionTest`).
- 평가한 카드는 `applyScheduling`이 `lastReviewedDate`를 세션일로 기록하고, `findAllNewCard`·`findAllReviewCard`·`countRemainingNewCards`는 `lastReviewedDate`가 null이거나 세션일보다 이전인 카드만 조회하므로 그날 큐에서 제외된다. NEW 카드는 AGAIN이어도 NEW로 남아 이 조건만으로 제외되고, REVIEW 카드는 AGAIN이어도 `nextReviewDate`가 세션일 + 1일 이상이라(`deck_presets`의 `lapse_min_interval`·`review_max_interval` CHECK가 1 이상) `nextReviewDate` 조건에서도 제외된다.
  NEW 카드를 AGAIN으로 평가해도 그날 다시 내지 않는 것은 SRS 명세의 의도다(현재 세션에서만 제외).
  당일 재학습을 넣으려면 위 세 쿼리의 `lastReviewedDate` 조건과 `SchedulingState`의 REVIEW AGAIN `nextReviewDate` 계산을 함께 바꾸고, `lastReviewedDate` 조건 없이 `nextReviewDate`만으로 오늘 평가한 카드를 제외하는 `countTodayReviewCards`(`recalcGoals`·`StudyTargetPoolCalculator`가 사용)도 확인한다(`StudyServiceTest`의 "세션 진입 이후 평가된 카드는 같은 세션에서 다시 나오지 않는다", `StudyServiceOnCardDeletionTest`의 "오늘(sessionDate) 평가된 NEW 카드는 availableNew에서 제외된다").
- 평가 요청 한 번은 `CardLearningState` 갱신·append-only `ReviewLog` 추가·세션 카운터 증가를 `EvaluationService.evaluate`의 한 트랜잭션에서 처리한다. 카드당 세션 1회는 현재 정책이라 그 세션의 기존 `ReviewLog` 조회로 막고, `review_logs`는 append-only 이력이라 (세션, 카드) 유니크를 두지 않는다.
  한 요청 안에서 cardId가 중복되면 마지막 항목만 반영하고, 이미 `ReviewLog`가 있는 카드는 `failedCardIds`에 담아 200으로 반환한다. 이 검증 실패를 예외로 바꾸면 같은 요청의 성공 항목까지 롤백된다.
- SM-2 계산은 `SchedulingState`·`Sm2Params`에만 두고, 이 두 파일이 엔티티나 repository를 import하지 않게 유지한다. import를 검사하는 테스트는 없고, 계산 결과는 `SchedulingStateTest`가 검사한다.
  EF 범위 1.30~5.00은 `SchedulingState`의 `MIN_EF`·`MAX_EF`와 `card_learning_states`·`review_logs`의 CHECK에, `timeMs` 상한 600000은 `review_logs`의 CHECK, `EvaluationController`의 clamp, `EvaluationItemValidator`에 같은 값으로 들어 있다.
  바꾸려면 코드 상수와 CHECK를 바꾸는 새 `V<n>` SQL을 같은 변경에 넣는다. clamp를 바꾸지 않으면 600000을 넘는 값은 새 상한 안이어도 600000으로 저장되고, EF 컬럼은 `DECIMAL(3,2)`라 9.99를 넘기려면 컬럼 타입도 바꾼다.
- `Rating`에 값을 더하면 `SchedulingStates`의 `again`·`fair`·`easy` 필드와 `pick`, `RatingCounts`와 `toRatingCounts`, `review_logs`의 `chk_log_rating`(새 `V<n>` SQL로 교체)을 한 변경에서 같이 고친다.
  `pick`은 `when`에 else가 없어 enum만 늘리면 컴파일이 실패하고, `toRatingCounts`는 새 값을 세지 않아도 컴파일되며, CHECK를 바꾸지 않으면 INSERT가 CHECK 위반으로 실패한다.
- 휴식일 기록(`StreakService.recordRest`)은 `study_days` upsert의 영향 행 수로 신규 여부를 판정한다(삽입 1, 이미 있음 2). 두 upsert의 update 절이 항상 `updated_at = NOW(6)`를 쓰므로 중복은 2이고, 코드의 0 분기는 실행되지 않는다. `recordStudied`는 1과 2 모두 streak을 다시 계산한다.
  update 절을 값이 바뀌지 않는 형태로 바꾸면 local `DB_URL`의 기본 JDBC 설정(`useAffectedRows=false`)에서 중복이 1로 반환돼 중복 요청마다 streak을 한 번 더 계산하고, 이를 검사하는 테스트는 없다. 배포 환경 `DB_URL`의 이 옵션은 Infisical에 있어 코드로 확인할 수 없다.
  `upsertStudied`의 `streak_type = 'DAILY_STUDY'`를 빼면 휴식일 행이 학습일로 바뀌지 않아 streak이 오르지 않는다(`StreakServiceTest`의 "휴식일로 기록되었을 때 학습 기록이 들어온다면 학습일로 수정되고 streak에 반영된다").
- 휴식일은 덱이 아니라 사용자 단위로 판정하고, 카드가 한 장도 없는 사용자는 제외한다(`ExistsBasedRestDayCheckService`). 마지막 `existsByUserIdAndDeletedAtIsNull`을 빼면 카드가 없는 사용자도 휴식일로 기록돼, 학습하지 않아도 연속 학습일 수가 초기화되지 않는다(`ExistsBasedRestDayCheckServiceUnitTest`의 "카드가 전혀 없으면 휴식일이 아니다").
- 바꾼 뒤 `./gradlew test --tests "com.whatever.caro.study.*"`를 돌린다. `StudyApi`나 `study` 루트 패키지의 공개 타입을 바꿨으면 `com.whatever.caro.bff.*`·`com.whatever.caro.withdrawal.*`를 `--tests` 대상에 추가한다.
  study의 엔티티·repository·테이블을 바꿨으면 `com.whatever.caro.withdrawal.*`를, 모듈 의존을 바꿨으면 `com.whatever.caro.ModularityTests`를 `--tests` 대상에 추가한다.
  `WithdrawnUserPurgeServiceTest`는 study 엔티티를 직접 생성해 repository로 저장한 뒤 삭제 여부를 확인한다.
