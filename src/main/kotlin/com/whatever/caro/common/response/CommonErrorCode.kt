package com.whatever.caro.common.response

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    INVALID_INPUT("C001", HttpStatus.BAD_REQUEST, "잘못된 입력입니다", "error.common.invalid_input"),
    INVALID_REQUEST("C002", HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "error.common.invalid_request"),
    NOT_FOUND("C003", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다", "error.common.not_found"),
    METHOD_NOT_ALLOWED("C004", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다", "error.common.method_not_allowed"),
    MISSING_PARAMETER("C005", HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다", "error.common.missing_parameter"),
    TYPE_MISMATCH("C006", HttpStatus.BAD_REQUEST, "파라미터 타입이 올바르지 않습니다", "error.common.type_mismatch"),
    MISSING_HEADER("C007", HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다", "error.common.missing_header"),
    INVALID_IDEMPOTENCY_KEY(
        "C008",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 Idempotency-Key 형식입니다",
        "error.common.invalid_idempotency_key",
    ),
    IDEMPOTENCY_KEY_CONFLICT(
        "C009",
        HttpStatus.CONFLICT,
        "동일한 Idempotency-Key로 다른 요청이 처리되었습니다",
        "error.common.idempotency_key_conflict",
    ),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(
        "C010",
        HttpStatus.CONFLICT,
        "이전 요청이 처리 중입니다. 잠시 후 다시 시도해주세요",
        "error.common.idempotency_request_in_progress",
    ),
    MISSING_API_VERSION(
        "C011",
        HttpStatus.BAD_REQUEST,
        "API 버전이 누락되었습니다",
        "error.common.missing_api_version",
    ),
    INVALID_API_VERSION(
        "C012",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 API 버전입니다: {0}",
        "error.common.invalid_api_version",
    ),
    UNSUPPORTED_API_VERSION(
        "C013",
        HttpStatus.BAD_REQUEST,
        "해당 엔드포인트가 지원하지 않는 API 버전입니다: {0}",
        "error.common.unsupported_api_version",
    ),
    INTERNAL_ERROR("C999", HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다", "error.common.internal_error"),
}
