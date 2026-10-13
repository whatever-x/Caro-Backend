package com.whatever.caro.card.internal.card.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class CardErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    NOT_FOUND("C001", HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다", "error.card.not_found"),
    FORBIDDEN("C002", HttpStatus.FORBIDDEN, "해당 카드에 접근할 권한이 없습니다", "error.card.forbidden"),
    INVALID_FIELDS("C003", HttpStatus.BAD_REQUEST, "카드 필드가 유효하지 않습니다", "error.card.invalid_fields"),
    LIMIT_EXCEEDED("C101", HttpStatus.CONFLICT, "덱에는 최대 200장의 카드를 저장할 수 있습니다", "error.card.limit_exceeded"),
}
