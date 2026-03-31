package com.whatever.caro.common.response

import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.MDC

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetail? = null,
) {
    companion object {
        fun <T> ok(
            data: T,
        ): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun fail(
            errorCode: ErrorCodeSpec,
            message: String? = null,
        ): ApiResponse<Nothing> =
            ApiResponse(
                success = false,
                error = ErrorDetail(
                    code = errorCode.code,
                    message = message ?: errorCode.message,
                    traceId = currentTraceId(),
                ),
            )

        fun failValidation(
            errorCode: ErrorCodeSpec,
            message: String? = null,
            fieldErrors: List<FieldError>,
        ): ApiResponse<Nothing> =
            ApiResponse(
                success = false,
                error = ErrorDetail(
                    code = errorCode.code,
                    message = message ?: errorCode.message,
                    traceId = currentTraceId(),
                    fieldErrors = fieldErrors,
                ),
            )

        /** OpenTelemetry가 MDC에 자동 주입하는 trace_id */
        private fun currentTraceId(): String? = MDC.get("trace_id")
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorDetail(
    /** 에러 코드 (예: "C001", "A003", "U001") */
    val code: String,
    /** 사용자에게 노출 가능한 에러 메시지 */
    val message: String,
    /** OpenTelemetry trace ID - 로그/트레이스 추적용 */
    val traceId: String? = null,
    /** Bean Validation 실패 시 필드별 에러 목록 */
    val fieldErrors: List<FieldError>? = null,
)

data class FieldError(
    val field: String,
    val message: String,
)
