package com.whatever.caro.study.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class StudyErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    SESSION_NOT_FOUND("S001", HttpStatus.NOT_FOUND, "학습 세션을 찾을 수 없습니다", "error.study.session_not_found"),
    SESSION_EXPIRED("S002", HttpStatus.CONFLICT, "오늘의 학습 세션이 아닙니다", "error.study.session_expired"),
}
