# Caro-Backend

## 명령
- 실행: `make run` (Docker와 Infisical 로그인 필요). 처음 실행할 때나 환경 문제로 기동이 실패하면 `make doctor`로 도구·Infisical 로그인·시크릿 키를 점검한다.
  IDE에서 실행할 때는 `make ide`로 MySQL·Redis 컨테이너를 기동하고 `.env`를 만든다. Swagger UI 주소는 `http://localhost:8081/swagger`이다.
- 테스트: `make test` (Docker만 필요하고 Infisical은 필요 없다). 일부만 실행할 때는 `./gradlew test --tests "com.whatever.caro.<module>.*"`를 쓴다.
- 검증: `make check`는 CI의 포맷 검사·테스트·커버리지 검증과 같지만 CI의 Docker 이미지 빌드 검사는 포함하지 않는다.
  빌드 설정(`build.gradle.kts`, `settings.gradle.kts`, `gradle/`)이나 `Dockerfile`·`docker/`를 바꿨으면 `docker build .`도 실행하고, Docker를 쓸 수 없으면 확인하지 못한 항목으로 보고한다.
  커버리지 하한(`build.gradle.kts`의 kover verify 블록)을 낮추거나 kover `excludes`를 늘리려면 사용자에게 확인받는다.
- 포맷: `make format`. 커밋하면 pre-commit 훅이 같은 포맷을 적용하고, push하면 pre-push 훅이 `./gradlew test` 전체를 실행한다. 훅이 없으면 `lefthook install`로 설치한다.
- 브랜치: `develop`에서 분기하고 PR의 base도 `develop`으로 한다. 이름은 `feat/`, `fix/`, `chore/`, `refactor/` 중 하나로 시작한다.
- 태그: `v*` 태그를 push하면 staging 배포가 실행되므로 push 전에 사용자에게 확인받는다. 태그를 만들 수 있는 커밋의 조건은 `.github/AGENTS.md`에 있다.

## 작업 순서
1. 파일을 수정하기 전에 그 파일이 속한 영역의 `AGENTS.md`를 Read로 읽고, 그 문서의 제약 안에서 수정한다.
   모듈 코드는 `src/main/kotlin/com/whatever/caro/<module>/AGENTS.md`, 테스트는 `src/test/kotlin/com/whatever/caro/AGENTS.md`를 읽는다.
   DB 마이그레이션과 시드는 `src/main/resources/db/migration/AGENTS.md`, 그 밖의 리소스는 `src/main/resources/AGENTS.md`를 읽는다.
   워크플로우와 빌드 설정(`build.gradle.kts`, `Dockerfile`)은 `.github/AGENTS.md`를 읽는다.
2. 다음 작업은 해당 스킬을 호출해 그 절차와 완료 조건을 따른다. API 추가·변경(새 버전, 요청·응답 형식, 인증 범위, `@Idempotent`)은 `add-endpoint`다.
   스키마 변경·DB에 저장되는 enum의 값 추가·시드 값 변경·데이터 보정은 `add-db-migration`, 모듈 의존 추가·모듈 신설은 `add-module-dependency`다.
   주기 작업(`@Scheduled`) 추가·변경은 `add-scheduled-job`, 이벤트 정의·발행과 리스너 추가·변경·삭제는 `add-domain-event`다.
   enum 값만 추가해도 그 값을 저장하는 컬럼의 CHECK를 바꾸지 않으면, 기존 테스트는 통과하고 새 값을 저장하는 요청만 실패한다.
3. 변경마다 테스트를 함께 쓰고, 끝나면 `make check`를 통과시킨다.
4. 작업이 한 PR 분량을 넘길 것 같으면 남은 범위를 정리해 보고하고 멈춘다.

## 규약
- `common`을 제외한 다른 모듈의 타입은 그 모듈 루트 패키지나 NamedInterface 패키지(`card`는 `api/<name>/`, 그 밖의 모듈은 예외를 두는 `exception/`)에서만 import한다.
  `common`은 `Type.OPEN`이라 하위 패키지까지 import할 수 있다. main 코드에서 그 밖의 패키지(`internal/` 등)를 참조하면 `ModularityTests`가 실패한다.
- `card` → `study` 방향은 순환이라 `allowedDependencies` 선언과 관계없이 `ModularityTests`가 실패한다. 두 모듈의 데이터가 함께 필요하면 `bff`에서 조합한다.
- 다른 모듈의 데이터는 그 모듈의 `*Api`로 조회하거나 변경을 요청하고, 다른 모듈이 반응해야 하는 상태 변화는 Modulith 이벤트로 발행한다.
- 스키마 변경은 `db/migration/<module>/`의 최대 버전 + 1인 새 `V<n>__<설명>.sql`로 한다. `develop`에 병합된 `V` 파일은 주석 한 줄도 고치지 않는다.
  고치면 이미 적용된 DB(local·staging·prod)에서 체크섬 불일치로 앱이 기동하지 않고, 테스트는 새 DB에 처음부터 적용하므로 CI가 이 오류를 검출하지 못한다.
- 시각은 `Instant`(UTC)로 저장하고 비교한다. 학습일은 요청의 `Client-Timezone`과 day cutoff로 계산한다.
  학습일을 서버 기준 날짜(`LocalDate.now()` 등)로 계산하면 UTC와 시차가 있는 사용자의 학습일이 실제 날짜와 달라진다.
- 현재 시각은 `Clock` 빈에서 얻는다. `Instant.now()`를 직접 부르면 `Clock.fixed`로 시각을 고정한 테스트가 그 값을 제어하지 못한다.
- API 응답은 `ApiResponse<T>`로 감싸고, 오류는 모듈별 `*ErrorCode`를 담은 `BusinessException`이나 그 하위 예외로 던진다.
  컨트롤러와 컨트롤러가 호출한 코드에서 던진 예외는 `GlobalExceptionHandler`가 상태 코드로 바꾼다. 에러 코드의 접두사·번호와 `messageKey` 규칙은 `src/main/kotlin/com/whatever/caro/common/AGENTS.md`를 따른다.
- API 버전은 매핑의 `version` 속성과 `API-Version` 헤더로 정하고, 헤더가 없으면 1.0이다. 새 매핑에는 `version`을 항상 쓴다.
  빠뜨리면 그 매핑은 지원하는 모든 버전의 요청에 응답하고, `version-N` 그룹 문서에서 빠져 `all-version`에만 나온다.
- 시크릿 값은 `application*.yaml`에 쓰지 않고 환경변수로 참조하며, 값은 Infisical `/application`에 둔다. `application-local.yaml`의 더미값만 예외다.
- `@ApplicationModuleListener`는 같은 이벤트를 두 번 받아도 안전하게 만든다. 리스너가 예외를 던져 FAILED가 된 발행을 `EventResubmitScheduler`가 다시 전달한다.
- 테스트가 실패하면 구현을 고친다. 테스트가 틀렸다고 판단해 테스트를 고치면 PR 설명에 근거를 쓰고, 테스트를 지우거나 비활성화하지 않는다.
  커버리지 하한은 전체 비율이라 테스트 하나를 지워도 CI가 통과할 수 있고, 그러면 그 테스트가 검출하던 회귀를 CI가 알리지 않는다.
- 작성한 코드는 다른 세션이 검토한다. 결함 지적에는 실패할 테스트 이름이나 재현 방법을, 결함이 없다는 결론에는 실행한 명령과 그 출력을 첨부한다.
  근거가 없는 지적에는 근거를 요청하고, 근거가 없는 "결함 없음" 결론은 승인으로 보지 않는다.
