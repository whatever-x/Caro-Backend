package com.whatever.caro.card.internal.library

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class LibraryErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    UNAVAILABLE("L001", HttpStatus.NOT_FOUND, "제공 덱을 찾을 수 없습니다", "error.library.unavailable"),
    INVALID_CONTENT("L002", HttpStatus.UNPROCESSABLE_ENTITY, "제공 덱을 추가할 수 없습니다", "error.library.invalid_content"),
}
