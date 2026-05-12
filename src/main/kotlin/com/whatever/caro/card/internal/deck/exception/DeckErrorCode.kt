package com.whatever.caro.card.internal.deck.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class DeckErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    NOT_FOUND("D001", HttpStatus.NOT_FOUND, "덱을 찾을 수 없습니다", "error.deck.not_found"),
    FORBIDDEN("D002", HttpStatus.FORBIDDEN, "해당 덱에 접근할 권한이 없습니다", "error.deck.forbidden"),
}
