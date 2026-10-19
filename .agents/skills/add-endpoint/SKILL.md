---
name: add-endpoint
description: REST 엔드포인트를 새로 추가하거나 기존 경로에 새 API 버전을 추가할 때의 절차와 완료 조건을 제공한다. 기존 엔드포인트의 요청·응답 형식 변경, 인증 허용 범위 변경, `@Idempotent` 부착에도 사용.
---

# 엔드포인트 추가

먼저 대상 모듈의 AGENTS.md를 읽는다. 여러 모듈의 `*Api` 결과를 합쳐 응답을 만들면 `bff`, 한 도메인의 규칙이면 그 도메인 모듈이다.
인증 정책은 `auth/AGENTS.md`, `@Idempotent`·API 버전·에러 코드는 `common/AGENTS.md`, 테스트는 `src/test/kotlin/com/whatever/caro/AGENTS.md`에 있으니 해당 단계 전에 읽는다.

1. 컨트롤러는 `<module>/internal/web/*Controller.kt`에 둔다. `card`는 `card/AGENTS.md`의 하위 도메인 패키지 배치를 따른다.
   클래스에 `@Tag`, 핸들러에 `@Operation(summary, description)`을 붙이고, 새 경로의 매핑에는 `version = "1.0"`을 명시하고 반환 타입은 `ResponseEntity<ApiResponse<T>>`로 한다.
2. 요청·응답 DTO는 `*Request`·`*Response`로 `internal/web/request/`·`internal/web/response/`에 둔다(`card`는 `internal/<card|deck>/dto/<용도>/`).
   요청 본문은 `@Valid`와 Bean Validation 애너테이션으로 검증한다.
3. 비즈니스 로직은 서비스에 두고, 다른 모듈 조회·에러·응답 래핑은 루트 AGENTS.md 규약을 따른다. 새 에러 코드는 `common/AGENTS.md`의 `*ErrorCode`·`messageKey` 항목을 따른다.
4. 인증 정책은 기본이 `ACTIVE`만 허용이며, 이 경우 `SecurityConfig`를 고치지 않는다. 다른 정책이 필요하면 사용자에게 확인받은 뒤 `auth/AGENTS.md`의 해당 항목대로 아래를 함께 고친다.
   - `SUSPENDED` 허용: `SecurityConfig`의 `anyRequest` 위 matcher와 `SecurityConfigAccessControlTest`의 `API_POLICY`
   - 액세스 토큰 없는 호출: `PublicEndpoints.AUTH`, 핸들러의 `@PublicApi`, `JwtAuthenticationFilterTest`의 `expectedPublicAuthPaths`(`SecurityConfig`는 고치지 않는다)
5. `@Idempotent`는 `common/AGENTS.md`의 기준으로 붙일지 정하고, 붙이면 핸들러에 `@RequestHeader("Idempotency-Key", required = true)` 파라미터를 두고 `@Operation` 설명에 그 헤더를 적는다(`EvaluationController.evaluate`).
   이미 있는 엔드포인트에 새로 붙이면 헤더를 보내지 않는 기존 클라이언트가 400(C007)을 받으므로 사용자에게 확인받는다.
6. 기존 매핑의 응답 필드나 enum 상수를 지우거나 이름·타입을 바꾸거나, 필수 요청 필드를 더하려면 사용자에게 확인받는다. 배포 뒤 구버전 앱이 응답을 해석하지 못하거나 400을 받는다.
   기존 클라이언트를 유지해야 하면 기존 매핑은 그대로 두고 7단계로 새 버전을 추가한다.
7. 같은 path+method에 새 버전을 추가하면 새 핸들러의 `version`을 기존 매핑과 같은 표기(`"2.0"`)로 올리고, 구버전 핸들러에 Kotlin `@Deprecated`를 붙인다(`CardController.getCardsByDeck`).
   Kotlin `@Deprecated`가 없으면(`@Operation(deprecated = true)`로는 안 된다) `all-version` 문서에서 두 버전이 한 operation으로 합쳐진다.
   그 버전의 그룹 빈이 `OpenApiConfig`에 없으면 추가한다. 없으면 오류 없이 그 버전은 `all-version` 문서에만 나온다.
   그룹 빈의 `versionMethodFilter`에 넘기는 문자열은 `common/AGENTS.md`의 `OpenApiConfig` 항목대로 매핑의 `version` 표기와 똑같이 쓴다.
8. 테스트는 `src/test/kotlin/com/whatever/caro/AGENTS.md`를 따른다.
   컨트롤러 계약(상태 코드, 입력 검증, 버전 라우팅)은 `*WebMvcTest`로, 바꾼 인증 정책은 4단계에서 고친 테스트로, 서비스 동작은 `@CaroModuleTest` 통합 테스트로 검증한다.
   WebMvc 슬라이스는 보안 설정을 제외하므로 인증 정책 검증에 쓰지 않는다.
9. `make check`를 통과시킨다. `make run`을 백그라운드로 실행해 `/swagger`의 그 버전 그룹(`version-N`)과 `all-version`에 새 경로가 나오는지 확인하고 종료한다.
   Docker나 Infisical 로그인이 없어 `make run`을 할 수 없으면 확인하지 못한 항목으로 보고한다. 인증이 필요한 엔드포인트의 동작은 테스트로 확인한다.

## 완료 조건
- 새 매핑에 `version`, `ResponseEntity<ApiResponse<T>>` 반환, `@Operation`이 있다
- 인증 정책이 기본(`ACTIVE`)이거나, 바꿨다면 4단계 하위 항목의 코드와 테스트 기대값에 같은 경로가 있다
- 새 버전이면 구버전 핸들러에 Kotlin `@Deprecated`가 있고 `OpenApiConfig`에 그 버전의 그룹 빈이 있다
- 컨트롤러 계약과 서비스 동작을 검증하는 테스트가 있고 `make check`를 통과한다
- PR 설명에 사용자 확인 결과(4~7단계), Swagger 확인 결과나 확인하지 못한 사유, 계약을 어기면 실패할 테스트 이름을 적는다
