# auth

- 액세스 토큰 없이 호출할 수 있어야 하는 컨트롤러 경로는 `PublicEndpoints.AUTH`에 넣고 해당 핸들러 메서드에 `@PublicApi`를 붙인 뒤 `JwtAuthenticationFilterTest`의 `expectedPublicAuthPaths`에도 같은 경로를 적는다.
  `@PublicApi`는 Swagger 문서의 인증 표시만 바꾸므로 `AUTH`에 넣지 않으면 토큰 없는 요청이 401을 받고, `@PublicApi`와 `PublicEndpoints.AUTH`가 어긋나면 `PublicEndpointsOpenApiConsistencyTest`가, `expectedPublicAuthPaths`와 어긋나면 `JwtAuthenticationFilterTest`가 실패한다.
- `PublicEndpoints.PATTERNS`에 넣은 경로는 `JwtAuthenticationFilter.shouldNotFilter`가 토큰을 파싱하지 않으므로, Authorization 헤더가 와도 `AuthUser` 인증 없이 `SecurityContext`에 `AnonymousAuthenticationToken`만 들어 있다고 보고 설계한다.
  이 경로에서 `SecurityUtil.currentUser()`는 401(A003)을 던지고, `@Idempotent`는 유효한 `Idempotency-Key`가 있을 때 같은 이유로 401, 헤더가 없거나 UUID가 아니면 그 전에 400이 난다.
- `PublicEndpoints.SWAGGER`에는 Swagger 문서 경로만 넣는다. 이 목록은 staging에서 `SwaggerSecurityConfig`의 `@Order(1)` 체인이 먼저 매치해 Basic Auth를 요구하므로, 다른 공개 경로를 넣으면 staging에서만 401이 난다(`SwaggerStagingBasicAuthTest`).
- 공개 경로(`PublicEndpoints.PATTERNS`)를 뺀 경로 권한은 `hasRole`/`hasAnyRole`로 허용할 status를 나열한다. `authenticated`를 쓰면 유효한 토큰이기만 하면 어떤 status든 통과한다.
- `UserStatus`에 값을 추가하면 그 status가 쓸 경로의 matcher에 직접 넣는다. 토큰의 `status`가 그대로 `ROLE_{status}`가 되므로 어느 matcher에도 없는 status는 공개 경로를 뺀 전부 403이다(`SecurityConfigAccessControlTest`의 `UNKNOWN` 케이스).
- 가입 미완료(`SUSPENDED`) 사용자가 쓸 경로는 `SecurityConfig`의 `authorize(anyRequest, hasRole("ACTIVE"))` 위에 추가하고, 그 matcher와 일치하는 실제 요청 경로(예: `/nicknames/**`면 `/nicknames/random`)와 허용 status를 `SecurityConfigAccessControlTest`의 `API_POLICY`에 넣는다.
  `anyRequest` 아래에 넣으면 Kotlin DSL은 기동 오류 없이 먼저 매치되는 `anyRequest` 규칙을 적용해 그 경로에 ACTIVE만 허용한다.
  테스트는 `API_POLICY`에 있는 경로만 확인하므로, 여기에 넣어야 SUSPENDED 요청이 403을 받아 테스트가 실패한다.
- 권한은 토큰 발급 시점의 status 스냅샷이고 폐기는 jti 단위다.
  status를 바꾸는 흐름은 `AuthService.completeRegistration`처럼 기존 jti를 블랙리스트에 넣고 재발급해야 하며, 로그아웃(`AuthService.logout`)과 탈퇴(`AuthService.withdrawUser`)도 요청에 쓴 jti만 막아 다른 기기의 access token은 만료까지 유효하다.
  전 기기 즉시 차단이 필요하면 설계 결정으로 올린다.
- `JwtExceptionFilter`보다 안쪽(`JwtAuthenticationFilter`, `RequestResponseCachingFilter`, 그 뒤 서블릿)에서 던진 예외는 `JwtExceptionFilter`가, 인증·인가 실패(401·403)는 `SecurityConfig`의 entry point와 access denied handler가 `ApiResponse`로 바꾼다.
  `GlobalExceptionHandler`는 `@RestControllerAdvice`라 DispatcherServlet 안에서 난 예외만 받으므로, 필터에서는 `BusinessException` 계열로 던진다. 그 외 예외는 `JwtExceptionFilter`가 500 `INTERNAL_ERROR`로 바꾼다.
  `JwtExceptionFilter`는 `JwtAuthenticationFilter`보다 먼저 등록해 바깥에 둔다. 안쪽에 두면 `JwtAuthenticationFilter`의 `InvalidAccessTokenException`을 어떤 필터도 처리하지 않아 container의 error dispatch로 전달되고, `INVALID_TOKEN`(A003) 응답이 나가지 않는다.
- 보안 체인에 `@Component` 필터를 새로 넣을 때는 `SecurityConfig.kt`의 `SecurityFilterRegistrationConfig`에 `isEnabled = false`인 `FilterRegistrationBean`을 같이 추가한다.
  없으면 Boot가 같은 빈을 servlet container에도 등록해, `OncePerRequestFilter`가 아닌 필터는 요청마다 두 번 실행된다. `OncePerRequestFilter`도 `@Order`가 보안 체인(-100)보다 앞서면(예: `HIGHEST_PRECEDENCE`) container에 등록된 쪽만 실행돼 보안 체인에서 지정한 순서가 적용되지 않는다.
  보안 체인 밖에서 실행되도록 container에만 등록한 `RequestResponseLoggingFilter`에는 추가하지 않는다.
- refresh token은 기기마다 `refresh:{userId}:{deviceId}`에 하나씩 저장되는 1회용 값이라, 기기 단위로 세션을 발급·폐기하는 엔드포인트(social-login, complete-registration, refresh, logout)는 `Device-Id` 헤더를 필수로 받는다.
  재발급에서 Device-Id가 다르거나 이미 쓴 refresh token이 다시 오면(동시든 순차든) `consumeToken`이 null을 돌려 401(A005)이 된다. logout은 Device-Id가 달라도 200이고 그 기기의 refresh token은 지워지지 않는다.
  forward key와 `token_pair` key를 읽고 바꾸는 `save`와 `consumeToken`은 `RefreshTokenRepository`의 Lua script 안에서 한다. 나눠 호출하면 동시 재발급이 둘 다 통과해 같은 refresh token으로 두 번 재발급되고, 두 키가 서로 다른 토큰 값을 저장한 채 남아 이후 재발급이 원인을 알려주는 로그 없이 401이 된다.
- Redis 연결 실패(`RedisConnectionFailureException`) 시 `TokenBlacklistRepository.isBlacklisted`는 true를 돌려, 서명과 만료 검사를 통과한 Bearer 토큰의 비공개 경로 요청을 401로 막는다(fail-closed). 블랙리스트 경로를 고칠 때 이 방향을 유지한다.
  연결 실패가 아닌 Redis 예외(command timeout의 `QueryTimeoutException` 등)는 잡지 않아 `JwtExceptionFilter`에서 500이 되고, fail-closed를 고정하는 테스트는 없다.
- access 토큰 수명은 `application.yaml`의 `app.jwt.access-token-expires-in`이 정본이고, `AuthService`의 모든 `tokenBlacklistRepository.add` 호출이 같은 값을 블랙리스트 TTL로 쓴다.
  값을 줄이면 이전에 발급된 토큰이 블랙리스트 항목보다 늦게 만료돼, 항목이 지워진 뒤 폐기한 토큰이 다시 인증을 통과하기 때문에, 줄이는 변경은 이미 발급된 토큰의 처리 방안을 먼저 확인받는다.
- 사용자 상태·데이터 변경은 auth에서 직접 하지 않고 `UserApi`를 호출해 처리한다. 탈퇴자 데이터 파기는 `AuthService.withdrawUser` 대신 `withdrawal` 모듈의 purge에 추가한다.
- auth를 고친 뒤에는 `./gradlew test --tests "com.whatever.caro.auth.*" --tests "com.whatever.caro.common.config.*"`로 좁혀 돌린다.
  auth 패턴만 돌리면 공개 경로·Swagger 계약을 지키는 `common.config`의 `PublicEndpointsOpenApiConsistencyTest`, `SwaggerLocalAccessTest`, `SwaggerDisabledInProdTest`가 빠진다.
  `AuthUser`나 `SecurityUtil`을 바꿨으면 card·bff 컨트롤러 테스트와 `IdempotencyInterceptorUnitTest`도 영향을 받으므로 `make check`로 확인한다. card·bff의 WebMvc 테스트는 `JwtAuthenticationFilter`를 mock으로 바꾸므로 필터 동작 변경은 auth 테스트만 검증한다.
