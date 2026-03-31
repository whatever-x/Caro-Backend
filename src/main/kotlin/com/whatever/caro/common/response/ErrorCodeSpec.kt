package com.whatever.caro.common.response

import org.springframework.http.HttpStatus

/**
 * 모듈별 분산 에러 코드의 공통 계약.
 * 새 모듈 추가 시 이 인터페이스를 구현하는 enum을 생성한다.
 * (예: CommonErrorCode, AuthErrorCode, UserErrorCode)
 */
interface ErrorCodeSpec {
    val code: String
    val status: HttpStatus

    /** 기본 메시지 (MessageSource fallback) */
    val message: String

    /** i18n 메시지 키 (예: "error.common.invalid_input") */
    val messageKey: String
}
