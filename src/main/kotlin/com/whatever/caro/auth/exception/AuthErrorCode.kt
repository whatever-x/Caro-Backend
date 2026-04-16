package com.whatever.caro.auth.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    UNAUTHORIZED("A001", HttpStatus.UNAUTHORIZED, "인증이 필요합니다", "error.auth.unauthorized"),
    ACCESS_DENIED("A002", HttpStatus.FORBIDDEN, "접근 권한이 없습니다", "error.auth.access_denied"),
    INVALID_TOKEN("A003", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다", "error.auth.invalid_token"),
    SOCIAL_FAILED("A004", HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다", "error.auth.social_failed"),
    INVALID_REFRESH_TOKEN("A005", HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다", "error.auth.invalid_refresh_token"),
}
