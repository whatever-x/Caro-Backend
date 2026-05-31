package com.whatever.caro.user.exception

import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class UserErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    NOT_FOUND("U001", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다", "error.user.not_found"),
    ALREADY_COMPLETED("U002", HttpStatus.CONFLICT, "이미 회원가입이 완료된 사용자입니다", "error.user.already_completed"),
    NICKNAME_DUPLICATED("U003", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다", "error.user.nickname_duplicated"),
    INVALID_NICKNAME("U004", HttpStatus.BAD_REQUEST, "유효하지 않은 닉네임입니다", "error.user.invalid_nickname"),
}
