package com.whatever.caro.card.internal.notetype.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class NoteTypeErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    NOT_FOUND("NT001", HttpStatus.NOT_FOUND, "노트 타입을 찾을 수 없습니다", "error.note_type.not_found"),
    NO_TEMPLATES("NT002", HttpStatus.BAD_REQUEST, "해당 노트 타입에 카드 템플릿이 없습니다", "error.note_type.no_templates"),
}
