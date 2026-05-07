package com.whatever.caro.common.web.idempotency

/**
 * Idempotency 보장이 필요한 endpoint에 사용
 * 해당 Annotation이 사용된 endpoint에는 다음이 강제되어야함:
 *   - Idempotency-Key 헤더 (UUID v4 or ULID)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val cacheInternalErrorResponse: Boolean = false,
)
