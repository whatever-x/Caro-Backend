package com.whatever.caro.common.openapi

import io.swagger.v3.oas.annotations.security.SecurityRequirements

/**
 * 해당 엔드포인트가 인증 없이 접근 가능함을 Swagger UI 에 표시.
 *
 * 이 어노테이션은 문서화(Swagger UI)에만 영향을 줌.
 * 실제 Spring Security 허용은 `PublicEndpoints.PATTERNS` 에 경로를 등록해야 함.
 * `PublicEndpointsOpenApiConsistencyTest`에서 두 설정 간 정합성을 검증.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@MustBeDocumented
@SecurityRequirements
annotation class PublicApi
