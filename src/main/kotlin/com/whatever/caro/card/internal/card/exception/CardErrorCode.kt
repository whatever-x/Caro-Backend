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
}
