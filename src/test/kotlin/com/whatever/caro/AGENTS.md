# test

- 테스트 클래스는 대상과 같은 패키지에 둔다. `@CaroModuleTest`는 `module` 속성을 받지 않고 테스트 클래스의 패키지로 부트스트랩할 모듈을 정한다.
  다른 모듈의 패키지에 두면 그 모듈이 부트스트랩돼 `@Import`한 설정이나 대상 빈이 없어 `NoSuchBeanDefinitionException`으로 실패한다.
  모듈 밖 하위 패키지(`com.whatever.caro.nomodule` 등)는 `Package ... is not part of any module!`로, 루트 패키지 `com.whatever.caro`는 루트가 모듈로 부트스트랩돼 `NoSuchBeanDefinitionException`으로 실패한다.
- 서비스는 `@CaroModuleTest` 통합 테스트를 먼저 쓰고 이름을 `*ServiceTest`로 짓는다. 실제 DB와 트랜잭션을 거쳐야 쿼리·제약·트랜잭션 경계의 오류가 드러난다.
  어노테이션 조합, 생성자 주입, 정리 훅은 `DeckServiceTest`의 구조를 따른다.
  외부 호출이나 분기 조합처럼 통합 테스트로 다루기 어려운 로직만 `*UnitTest`로 보탠다. 협력자는 `mockk`로 만들어 생성자에 넘기고, 시각을 단언하면 `Clock.fixed`로 고정한다(`CardServiceUnitTest`의 `fixedNow`).
- 스펙 클래스에는 `@Transactional`을 붙이지 않는다. 스펙 전체가 한 트랜잭션에서 돌면 대상 코드의 `@Transactional` 누락이 가려지고, 커밋 후에 실행되는 리스너는 호출되지 않는다.
  리포지토리 테스트는 `StudySessionRepositoryTest`처럼 트랜잭션 없이 호출하고, `@Transactional`이 없는 `@Modifying` 메서드를 직접 부를 때만 그 호출을 `transactionTemplate.execute {}`로 감싼다.
  감싸지 않으면 `No EntityManager with actual transaction available`(`flushAutomatically = true`일 때)이나 `No active transaction for update or delete query`로 실패한다.
- 다른 모듈 빈이 필요하면 같은 모듈의 기존 `@CaroModuleTest`와 `mode`·`extraIncludes`·`@Import`를 똑같이 쓴다. 이 값이 같은 스펙끼리 테스트 컨텍스트와 MySQL·Redis 컨테이너를 공유한다.
  이 값이나 `@MockitoBean`·`@ActiveProfiles`·`@TestPropertySource`가 하나라도 다르면 컨텍스트와 컨테이너가 한 쌍 더 뜨고, JVM 힙을 넘으면 전체 테스트가 종료된다.
  `@CaroModuleTest`에서 mock 빈이 필요하면 스펙마다 `@MockitoBean`으로 두지 말고, 그 모듈 스펙들이 함께 `@Import`하는 `@TestConfiguration` + `@Primary` 설정에 넣는다.
- STANDALONE 부트스트랩에는 다른 모듈의 빈이 없다. 대상 모듈의 빈이 다른 모듈의 `*Api`를 생성자로 받으면, 그 모듈의 `@CaroModuleTest`는 리포지토리만 쓰는 스펙까지 `NoSuchBeanDefinitionException`으로 실패한다.
  그 빈은 모듈마다 한 가지 방식으로 채운다. 다른 모듈의 데이터 변화까지 단언하는 모듈은 `extraIncludes`나 `mode`로 실제 모듈을 포함한다(auth는 `user`, withdrawal은 `ALL_DEPENDENCIES`).
  값만 받아 쓰는 모듈은 그 모듈 스펙들이 함께 `@Import`하는 `@TestConfiguration` + `@Primary` mock에 넣는다(study는 `MockDeckPresetApiConfig`).
  `MockDeckPresetApiConfig`는 다른 모듈 빈이 아닌 study의 `RestDayCheckService`도 relaxed mock으로 바꿔 `isRestDay`가 기본 false이므로, 휴식일 분기는 `every`로 스텁해 검증한다(`StreakServiceTest`).
- 이벤트 리스너 테스트는 `CardLearningStateEventListenerTest`의 "CardsCreatedEvent를 수신하면 LearningState를 생성한다"처럼 `transactionTemplate.execute {}` 안에서 발행하고 `await().untilAsserted`로 기다린다.
  `@ApplicationModuleListener`는 커밋 후 다른 스레드에서 실행되므로, 트랜잭션 없이 발행하면 호출되지 않고(미완료 `event_publication` 행은 남는다) `await` 없이 단언하면 실패한다.
  발행하는 쪽 테스트는 발행까지만 단언하고, 처리 결과는 그 리스너의 테스트에서 단언한다. 발행하는 쪽이 리스너 결과까지 단언하면 리스너가 바뀔 때 관련 없는 테스트가 깨진다.
- 통합 테스트는 직접 만든 행을 FK 자식에서 부모 순으로 `afterTest`에서 `deleteAllInBatch()`한다. 대상 메서드가 발행한 이벤트로 리스너가 다른 테이블에 쓰는 행은 발행하는 쪽에서 기다리거나 지우지 않는다.
  대신 테이블의 행 수나 내용을 단언하는 스펙은 `beforeTest`에서 그 테이블을 비우고 시작한다(기준 데이터 테이블은 아래 bullet을 따른다).
  다른 스펙의 리스너가 늦게 쓴 행은 같은 컨텍스트에서 뒤에 실행되는 스펙(패키지를 포함한 클래스 이름의 사전순)까지 남는다.
  예를 들어 `StreakServiceTest`가 시작 전에 `study_days`를 비우지 않으면, 앞 스펙이 남긴 행 때문에 "최초 학습이면 streak이 1로 생성되고 학습일이 DAILY_STUDY로 기록된다"가 실패한다.
- 기준 데이터(`deck_presets`·`note_types`·`card_templates`)는 시드를 전제하지 말고 스펙 안에서 만든다.
  시드는 card 모듈을 포함하는 컨텍스트에서만 DB를 만들 때 한 번 INSERT되므로, study·auth·user의 STANDALONE 컨텍스트에는 없고 card 컨텍스트에서도 앞선 스펙이 지우면 다시 생기지 않는다.
  그래서 기준 데이터 테이블은 `deleteAllInBatch()`로 통째로 지우지 말고 스펙이 만든 행만 지운다. 시드 프리셋(id 1)이 없으면 `DeckPresetEventListener`가 오류 없이 덱의 프리셋 연결을 건너뛴다.
- 스펙에서 mock 호출 횟수를 검증하면 `afterTest`에서 `clearMocks`를 부른다. `@MockitoBean`은 `it`마다 초기화되지만 mockk로 만든 mock(스펙 필드, `@TestConfiguration` 빈)은 그렇지 않다.
  Kotest 기본 SingleInstance 모드라 mockk mock이 호출을 누적해, `verify(exactly = 0)`가 앞선 테스트의 호출 때문에 실패한다(`StudyServiceTest`).
  스펙 수준 스텁을 유지하려면 `clearMocks(x, answers = false)`로 호출 기록만 지운다(`AuthServiceTest`). 정리 훅은 `afterTest`로 쓴다. `withData`가 만든 테스트에는 `beforeEach`/`afterEach`가 실행되지 않는다.
- 컨트롤러 슬라이스 테스트는 `CardControllerWebMvcTest`의 구조를 따르고 이름을 `*WebMvcTest`로 짓는다. 다른 이름이면 `GlobalExceptionHandler` 변경을 검증하는 `--tests "*WebMvcTest"` 실행에 포함되지 않는다.
  템플릿의 `@MockitoBean` 필드는 지우지 않고, 대상 컨트롤러가 생성자로 받는 빈(서비스, `Clock`)을 `@MockitoBean`으로 추가한다. 빠진 빈이 있으면 컨텍스트 로딩이 실패하고 `Caused by` 끝에 `NoSuchBeanDefinitionException`이 나온다.
  이 슬라이스에서만 mockk 대신 Mockito를 쓴다. Spring이 컨트롤러에 주입할 mock 빈을 등록하는 기본 기능(`@MockitoBean`)이 Mockito 기반이다.
  대상 컨트롤러마다 컨텍스트가 따로 떠서 `@MockitoBean`이 컨텍스트를 늘리지 않고, `it`마다 초기화돼 호출 기록도 누적되지 않는다.
- HTTP 예외 응답은 `error.code`만 값으로 단언한다. `error.message`는 기획·번역 수정으로 바뀌므로 문구 리터럴로 단언하지 않는다.
  문구를 검증해야 하면 같은 요청을 `Accept-Language` ko·en으로 보내 두 `error.message`가 서로 다르고, `{`가 남지 않고, 메시지 인자(누락된 헤더 이름 등)를 포함하는지 단언한다.
- `@WebMvcTest`에서 서비스를 스텁할 때는 리터럴 인자로 `given`/`verify`하고(`DeckBFFControllerWebMvcTest`), 매처가 필요하면 `any(T::class.java) ?: 기본값`처럼 null을 넘기지 않게 쓴다.
  `any()`는 항상, `eq()`는 `String` 같은 참조형 인자에서 Kotlin non-null 파라미터에 null을 넘겨 NPE를 낸다. 이때 해제되지 않은 매처 때문에 그 스펙이 `InvalidUseOfMatchersException`으로 중단되거나 다음 스텁이 잘못 묶인다.
- `@Idempotent` 엔드포인트의 `@WebMvcTest`는 `RequestResponseCachingFilter`를 직접 넣은 `MockMvc`로 호출하고, `IdempotencyRepository.acquireIdempotencyProcessing`은 null-safe 매처로 true를 반환하게 스텁한다(없으면 409 C010).
  `MockMvcBuilders.webAppContextSetup(ctx).addFilters<DefaultMockMvcBuilder>(RequestResponseCachingFilter()).build()`
  템플릿의 `@AutoConfigureMockMvc`를 `addFilters = true`로 바꾸는 방식은 mock 필터가 요청을 컨트롤러에 전달하지 않아 200 빈 응답으로 통과하므로 쓰지 않는다.
- 실패 원인은 콘솔이 아니라 `build/reports/tests/test/index.html`이나 `build/test-results/test/TEST-*.xml`에서 본다. `build.gradle.kts`에 `testLogging`이 없어 콘솔에는 테스트 이름과 예외 클래스·파일:줄만 나오고 메시지가 없다.
  Gradle이 `exit value 3`으로 끝나면 테스트 JVM(`maxHeapSize = "1g"`)이 `-XX:+ExitOnOutOfMemoryError`로 종료된 것이고, 원인은 콘솔의 `OutOfMemoryError`와 `build/test-gc.log`에만 있다. 새 스펙의 컨텍스트 조합부터 확인한다.
  생성자 인자가 있는 스펙이 모두 `should have a zero-arg constructor`로 실패하면, 생성자 주입용 `SpringExtension`을 등록하는 `io.kotest.provided.ProjectConfig`의 이름이나 패키지가 바뀌었는지 확인한다.
- 이름이 `*Dto`·`*Request`·`*Response`·`*Event` 등이거나 `@Entity`·`@MappedSuperclass`·`@Configuration`·`@ConfigurationProperties`가 붙은 클래스는 `build.gradle.kts`의 kover `excludes`로 커버리지 집계에서 제외된다.
  이름 접미사만 보므로 로직이 있는 클래스도 이 접미사로 끝나면 제외된다. 그런 클래스와 엔티티 메서드(`StudySession.completeIfGoalAchieved` 등)는 테스트가 없어도 `koverVerify`가 통과하므로 테스트를 직접 추가하고, 새 로직 클래스 이름에는 그 접미사를 쓰지 않는다.
