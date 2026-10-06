---
name: add-endpoint
description: REST 엔드포인트를 추가할 때 공통 절차와 검증 기준을 제공한다. 새 API나 기존 API의 새 버전 추가에 사용.
---

# 엔드포인트 추가

먼저 대상 모듈의 AGENTS.md를 읽는다. 화면용 조합이면 `bff`, 도메인 규칙이면 그 도메인 모듈이다.

1. 컨트롤러는 `<module>/internal/web/*Controller.kt`에 둔다. `card` 모듈은 하위 도메인별 배치가 섞여 있으므로(`internal/card/CardController.kt`, `internal/deck/controller/DeckController.kt`) 같은 하위 도메인의 기존 컨트롤러 옆에 둔다. 매핑에 `version = "1.0"`을 명시하고 반환은 `ResponseEntity<ApiResponse<T>>`.
2. 요청/응답 DTO는 `internal/web/request/`, `internal/web/response/`에 `*Request`/`*Response`로 둔다(`card`는 `dto/<용도>/`). 요청 검증은 `@Valid` + Bean Validation 애너테이션.
3. 비즈니스 로직은 서비스에, 다른 모듈 호출은 그 모듈의 `*Api` 인터페이스로만. 오류는 모듈 `*ErrorCode` + `BusinessException`.
4. 인증 정책을 정한다. 기본은 `ACTIVE`만 통과한다. 가입 미완료(`SUSPENDED`)도 써야 하면 `SecurityConfig.authorizeHttpRequests`에 매처를 추가하고 `SecurityConfigAccessControlTest`의 `API_POLICY`에 같은 줄을 넣는다. 인증 없이 열려면 `PublicEndpoints.PATTERNS` + `@PublicApi`.
5. 재시도로 중복 생성될 수 있는 POST에는 `@Idempotent`.
6. 테스트는 대상과 같은 패키지에 추가하거나 기존 테스트를 보강한다. 컨트롤러 `*WebMvcTest`로 상태 코드·인증 정책·입력 검증·버전 라우팅 중 해당 계약을 검증한다. 비즈니스 로직이 추가·변경되면 서비스 단위 테스트로 정상·오류·경계 조건을 검증하고, DB나 트랜잭션 동작에 의존하면 통합 테스트를 사용한다.
7. `make check`를 통과시킨다. Swagger 노출은 `make run` 후 `/swagger`에서 확인하거나, Infisical이 없으면 api-docs 계약 테스트(`SwaggerLocalAccessTest` 등)로 확인한다. 인증이 필요한 엔드포인트의 동작 확인은 로컬 토큰 발급 경로가 아직 없으므로 통합 테스트로 한다.

## 완료 조건
- 새 매핑에 `version` 명시, `ApiResponse` 래핑
- 인증 정책이 `SecurityConfig`와 `API_POLICY`에 같은 내용으로 존재(기본 `ACTIVE` 정책이면 확인만)
- API 계약과 변경된 비즈니스 동작에 맞는 테스트 추가·보강, `make check` 통과
- PR 설명에 실패 시 드러나는 앵커(테스트 이름) 기재
