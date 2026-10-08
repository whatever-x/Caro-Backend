# resources

- 환경마다 값이 다른 키만 `application-<profile>.yaml`에 두고 나머지는 `application.yaml`에 둔다. 프로파일 yaml의 값이 base의 같은 키보다 우선하므로, base만 고치면 같은 키가 있는 프로파일에는 변경이 반영되지 않고 오류도 나지 않는다.
  base 값을 바꾸기 전에 `grep -n '<키의 마지막 이름>:' src/main/resources/application*.yaml`(예: `probability:`)로 프로파일에 같은 키가 있는지 확인한다.
  yaml이 중첩 구조라 점으로 이은 전체 키로는 검색되지 않고, `enabled`·`port`처럼 흔한 이름은 상위 키가 같은 줄만 같은 키로 본다.
- `local` 프로파일이 기본값 없는 `${ENV_VAR}`나 빈 기본값 `${ENV_VAR:}`로 참조하게 되는 키(`application.yaml`에 추가하고 `application-local.yaml`이 다른 값으로 지정하지 않는 키 포함)는 `Makefile`의 `REQUIRED_SECRETS`와 `README.md`의 환경변수 표에 추가한다.
  Infisical dev 환경 `/application` 등록은 사용자에게 요청한다. `REQUIRED_SECRETS`에 없으면 `make doctor`가 그 키를 검사하지 않는다.
  `REDIS_*`처럼 기동 단계에서 쓰이지 않는 키는 Infisical에 없어도 `make run` 기동이 성공하므로, 기동 성공을 등록 확인의 근거로 쓰지 않는다.
- `application-local.yaml`의 더미값(`app.jwt.secret`, `app.email.*`의 `${EMAIL_*:더미}` 기본값)은 지우지 않고, 값을 검증하거나 `@Value`로 받는 키를 새로 추가하면 같은 방식으로 더미를 둔다.
  `make test`는 환경변수 없이 `local` 프로파일로 기동하므로, 더미가 없으면 그 검증이 실패해 전체 컨텍스트 테스트와 해당 모듈 테스트가 실패한다.
- 환경변수로 받는 새 `app.*` 속성은 yaml에 빈 기본값 `${ENV_VAR:}`로 쓰고, properties 클래스에 `@Validated`와 `@field:NotBlank`(컬렉션은 `@field:NotEmpty`)를 둔다. 환경변수가 없으면 빈 값이 바인딩돼 기동이 실패한다.
  `@ConfigurationProperties`는 해석하지 못한 `${ENV_VAR}`를 문자열 그대로 바인딩하므로, `:`를 빠뜨리면 `@NotBlank`를 통과한 채 그 문자열이 키나 비밀값으로 쓰인다.
  중첩 클래스로 받으면 상위 필드에 `@field:Valid`를 둔다. 없으면 중첩 클래스의 제약이 실행되지 않은 채 기동된다. 이 바인딩 동작을 검사하는 테스트는 없다.
- `app.*` 키를 추가하거나 값을 바꾸면 받는 클래스를 `grep -rn 'prefix = "app.<이름>"' src/main/kotlin`으로 찾아, 그 클래스가 속한 모듈의 AGENTS.md를 먼저 읽고 파라미터를 같이 고친다.
  클래스에 없는 키는 오류 없이 무시되고, 기본값 없는 파라미터에 대응하는 키가 없으면 생성자 바인딩이 실패해 기동되지 않는다.
  `@ConfigurationProperties` 밖에서 받는 키는 둘이다. `app.swagger.auth`는 `@Profile("staging")`인 `SwaggerSecurityConfig`의 `@Value`가 받아 `application-staging.yaml`에만 있고, `app.schedule.cron.*`는 아래 항목이 다룬다.
- `app.schedule.cron.*` 키 이름은 `StaleSessionSweeper`와 `WithdrawnUserPurgeSweeper`의 `@Scheduled` placeholder와 일치시킨다. 키를 지우거나 이름이 다르면 폴백 `-`가 적용돼 오류 없이 그 스케줄만 등록되지 않는다.
  스케줄을 비활성화하려면 값을 따옴표로 감싼 `"-"`로 둔다. 따옴표 없는 `-`는 YAML 파싱 오류이고, 빈 문자열은 기동이 실패한다.
  이 키를 검사하는 테스트가 없으니, 키를 바꾸면 `grep -rhoE 'app\.schedule\.cron\.[a-z-]+' src/main/kotlin`의 이름이 모두 `application.yaml`의 `cron:` 아래에 있는지 확인한다.
- `messages.properties`(ko 외 모든 로케일의 기본)와 `messages_ko.properties`의 키 집합을 일치시킨다. `messages_ko.properties`에 없으면 한국어 요청이 영어 문구를, `messages.properties`에 없으면 ko 외 요청이 enum의 한국어 기본 문구를 받는다.
  일치 여부는 아래 명령의 출력이 비는지로 확인한다.
  `diff <(grep -oE '^[^#![:space:]=:]+' src/main/resources/messages.properties | sort) <(grep -oE '^[^#![:space:]=:]+' src/main/resources/messages_ko.properties | sort)`
- `{0}`을 쓰는 메시지 문구의 작은따옴표는 `''`로 쓴다. 인자가 있으면 MessageFormat이 `'`를 quote 문자로 처리해 `'`가 출력에서 제거되고 `{0}`이 치환되지 않는다.
- `nickname/<locale>/<category>.txt`를 고치기 전에 `src/main/kotlin/com/whatever/caro/nickname/AGENTS.md`를 읽는다. 문자 집합·길이 상한과 카테고리·로케일 추가 절차가 거기 있다.
  로케일 디렉터리를 새로 만들면 `NicknameLocales.SUPPORTED`에도 등록한다. 등록하지 않으면 `WordDictionary`가 그 디렉터리를 읽지 않아 그 로케일 요청이 오류 없이 영어 단어를 받는다.
- 시드 `db/seed/<모듈>/R__*.sql`을 고치기 전에 `src/main/resources/db/migration/AGENTS.md`를 읽는다. 폴더 이름 규칙과 시드가 의존하는 시각 컬럼 `DEFAULT`가 거기 있다.
- OTLP 주소는 세 키가 `${OTLP_ENDPOINT:http://localhost:4318}`를 함께 쓰므로 보통 환경변수 `OTLP_ENDPOINT`로 바꾼다.
  yaml에 주소를 직접 쓰면 `management.otlp.metrics.export.url`, `management.opentelemetry.logging.export.otlp.endpoint`, `management.opentelemetry.tracing.export.otlp.endpoint`를 모두 바꾼다.
  하나만 바꾸면 나머지 신호는 이전 주소로 export된다.
- prod의 Swagger 비활성화는 `application-prod.yaml`의 `springdoc.api-docs.enabled: false`와 `springdoc.swagger-ui.enabled: false`가 담당하므로 지우지 않는다.
  `SecurityConfig`가 Swagger 경로를 모든 프로파일에서 인증 없이 허용하므로, 두 키를 지우면 prod에서 Swagger가 인증 없이 노출되고 `SwaggerDisabledInProdTest`가 실패한다.
  `OpenApiConfig`의 `@Profile("!prod")`는 문서 정보·customizer·그룹 빈만 prod에서 제외하고 노출 여부는 정하지 않는다.
- `springdoc.swagger-ui.urls-primary-name`에는 group명(`version-1`)이 아니라 `OpenApiConfig`의 displayName(`API version 1`)을 쓴다.
  다르면 오류 없이 Swagger UI가 그룹 목록의 첫 항목을 기본 문서로 표시하고, 이 값을 검사하는 테스트는 없다.
- `server.*`나 `management.server.*`를 바꾸면 `make run`을 백그라운드로 실행해 바뀐 포트로 요청하고, `localhost:<management.server.port>/actuator/health`(기본 9090)가 `UP`인지 확인한 뒤 종료한다.
  테스트는 `@SpringBootTest(webEnvironment = MOCK)`이거나 웹 서버를 띄우지 않는 `@CaroModuleTest`·`@WebMvcTest`라 실제 포트에 bind하지 않는다.
  Docker나 Infisical 로그인이 없어 `make run`을 할 수 없으면 확인하지 못한 항목으로 보고한다. OTLP 수집기가 없으면 metrics WARN·traces ERROR 로그가 출력되지만 기동 실패가 아니다.
- staging·prod yaml의 값을 바꾸면 사용자에게 확인받고, 기본값 없는 `${ENV_VAR}`나 빈 기본값 `${ENV_VAR:}`를 `application.yaml`이나 staging·prod yaml에 추가하면 Infisical staging·prod 환경 등록을 사용자에게 요청한다.
  두 프로파일을 로드하는 테스트는 `@ActiveProfiles`로 local을 대체하는 `SwaggerStagingBasicAuthTest`와 `SwaggerDisabledInProdTest`뿐이라, local 더미 대신 `@TestPropertySource` 목록에서 값을 받는다. 새 키 때문에 이 둘의 컨텍스트 로딩이 실패하면 그 목록에 넣는다.
  두 테스트는 컨텍스트 로딩과 Swagger 노출·인증만 검사해, 로그 레벨이나 sampling 비율 같은 값이 틀려도 테스트가 통과한다.
