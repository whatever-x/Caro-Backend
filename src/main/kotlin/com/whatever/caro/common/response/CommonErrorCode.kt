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
    INTERNAL_ERROR("C999", HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다", "error.common.internal_error"),
}
