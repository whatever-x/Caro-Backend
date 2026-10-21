package com.whatever.caro.bff.internal

import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.ErrorCodeSpec
import org.springframework.http.HttpStatus

enum class StudyReportErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val message: String,
    override val messageKey: String,
) : ErrorCodeSpec {
    INVALID_PERIOD("R001", HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다", "error.report.invalid_period"),
    INVALID_CURSOR("R002", HttpStatus.BAD_REQUEST, "목록 위치 정보가 올바르지 않습니다", "error.report.invalid_cursor"),
    TARGET_NOT_FOUND("R003", HttpStatus.NOT_FOUND, "학습 기록을 찾을 수 없습니다", "error.report.target_not_found"),
}

class InvalidReportPeriodException(
    message: String,
) : BusinessException(StudyReportErrorCode.INVALID_PERIOD, message = message)

class InvalidReportCursorException(
    message: String,
) : BusinessException(StudyReportErrorCode.INVALID_CURSOR, message = message)

/**
 * 없는 덱·카드와 다른 사용자의 덱·카드를 구분하지 않고 같은 404로 응답한다.
 */
class ReportTargetNotFoundException(
    message: String,
) : BusinessException(StudyReportErrorCode.TARGET_NOT_FOUND, message = message)
