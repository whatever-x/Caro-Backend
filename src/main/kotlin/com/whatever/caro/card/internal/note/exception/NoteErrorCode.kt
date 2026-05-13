package com.whatever.caro.card.internal.note.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class NoteErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    NOT_FOUND("N001", HttpStatus.NOT_FOUND, "노트를 찾을 수 없습니다", "error.note.not_found"),
    FORBIDDEN("N002", HttpStatus.FORBIDDEN, "해당 노트에 접근할 권한이 없습니다", "error.note.forbidden"),
    INVALID_FIELDS("N003", HttpStatus.BAD_REQUEST, "노트 필드가 유효하지 않습니다", "error.note.invalid_fields"),
}
