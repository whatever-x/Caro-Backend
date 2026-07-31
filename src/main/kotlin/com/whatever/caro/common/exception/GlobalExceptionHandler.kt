package com.whatever.caro.common.exception

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.CommonErrorCode
import com.whatever.caro.common.response.ErrorCodeSpec
import com.whatever.caro.common.response.FieldError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.accept.InvalidApiVersionException
import org.springframework.web.accept.MissingApiVersionException
import org.springframework.web.accept.NotAcceptableApiVersionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.Locale

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler(
    private val messageSource: MessageSource,
) {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        e: BusinessException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val message = resolveMessage(e.errorCode, e.args, locale)
        logger.warn { "${e.errorCode}: ${e.message}" }
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.fail(e.errorCode, message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        e: MethodArgumentNotValidException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors = e.bindingResult.fieldErrors.map { error ->
            FieldError(field = error.field, message = error.defaultMessage ?: "유효하지 않은 값입니다")
        }
        val message = resolveMessage(CommonErrorCode.INVALID_INPUT, null, locale)
        logger.warn { "Validation failed: ${fieldErrors.map { "${it.field}: ${it.message}" }}" }
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.failValidation(CommonErrorCode.INVALID_INPUT, message, fieldErrors))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(
        e: HttpMessageNotReadableException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Unreadable request body: ${e.message}" }
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse.fail(
                    CommonErrorCode.INVALID_REQUEST,
                    resolveMessage(CommonErrorCode.INVALID_REQUEST, null, locale),
                ),
            )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        e: MissingRequestHeaderException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Missing header: ${e.headerName}" }
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse.fail(
                    CommonErrorCode.MISSING_HEADER,
                    resolveMessage(CommonErrorCode.MISSING_HEADER, arrayOf(e.headerName), locale),
                ),
            )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(
        e: MissingServletRequestParameterException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Missing parameter: ${e.parameterName}" }
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse.fail(
                    CommonErrorCode.MISSING_PARAMETER,
                    resolveMessage(CommonErrorCode.MISSING_PARAMETER, arrayOf(e.parameterName), locale),
                ),
            )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        e: MethodArgumentTypeMismatchException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Type mismatch: ${e.name}" }
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse.fail(
                    CommonErrorCode.TYPE_MISMATCH,
                    resolveMessage(CommonErrorCode.TYPE_MISMATCH, arrayOf(e.name), locale),
                ),
            )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(
        e: NoResourceFoundException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Resource not found: ${e.resourcePath}" }
        return ResponseEntity
            .status(CommonErrorCode.NOT_FOUND.status)
            .body(ApiResponse.fail(CommonErrorCode.NOT_FOUND, resolveMessage(CommonErrorCode.NOT_FOUND, null, locale)))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        e: HttpRequestMethodNotSupportedException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Method not supported: ${e.method}" }
        return ResponseEntity
            .status(CommonErrorCode.METHOD_NOT_ALLOWED.status)
            .body(
                ApiResponse.fail(
                    CommonErrorCode.METHOD_NOT_ALLOWED,
                    resolveMessage(CommonErrorCode.METHOD_NOT_ALLOWED, null, locale),
                ),
            )
    }

    @ExceptionHandler(MissingApiVersionException::class)
    fun handleMissingApiVersion(
        e: MissingApiVersionException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Missing API version" }
        return ResponseEntity
            .status(e.statusCode)
            .body(
                ApiResponse.fail(
                    CommonErrorCode.MISSING_API_VERSION,
                    resolveMessage(CommonErrorCode.MISSING_API_VERSION, null, locale),
                ),
            )
    }

    /**
     * 요청 버전은 유효하지만, 해당 엔드포인트가 그 버전을 제공하지 않는 경우
     */
    @ExceptionHandler(NotAcceptableApiVersionException::class)
    fun handleNotAcceptableApiVersion(
        e: NotAcceptableApiVersionException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val version = e.version.take(MAX_VERSION_LENGTH)
        logger.warn { "API version not supported by endpoint: $version" }
        return ResponseEntity
            .status(e.statusCode)
            .body(
                ApiResponse.fail(
                    CommonErrorCode.UNSUPPORTED_API_VERSION,
                    resolveMessage(CommonErrorCode.UNSUPPORTED_API_VERSION, arrayOf(version), locale),
                ),
            )
    }

    @ExceptionHandler(InvalidApiVersionException::class)
    fun handleInvalidApiVersion(
        e: InvalidApiVersionException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val version = e.version.take(MAX_VERSION_LENGTH)
        logger.warn { "Invalid API version: $version" }
        return ResponseEntity
            .status(e.statusCode)
            .body(
                ApiResponse.fail(
                    CommonErrorCode.INVALID_API_VERSION,
                    resolveMessage(CommonErrorCode.INVALID_API_VERSION, arrayOf(version), locale),
                ),
            )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        e: ResponseStatusException,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = errorCodeFor(e.statusCode)
        if (e.statusCode.is5xxServerError) {
            logger.error(e) { "Response status exception (${e.statusCode})" }
        } else {
            logger.warn { "Response status exception (${e.statusCode}): ${e.reason}" }
        }
        return ResponseEntity
            .status(e.statusCode)
            .headers(e.headers)
            .body(ApiResponse.fail(errorCode, resolveMessage(errorCode, null, locale)))
    }

    /** 상태 코드에 대응하는 에러 코드가 있으면 그것을 쓰고, 없으면 4xx/5xx 기본값으로 떨어진다. */
    private fun errorCodeFor(
        status: HttpStatusCode,
    ): ErrorCodeSpec =
        when {
            status.is5xxServerError -> CommonErrorCode.INTERNAL_ERROR
            status.value() == HttpStatus.NOT_FOUND.value() -> CommonErrorCode.NOT_FOUND
            status.value() == HttpStatus.METHOD_NOT_ALLOWED.value() -> CommonErrorCode.METHOD_NOT_ALLOWED
            else -> CommonErrorCode.INVALID_REQUEST
        }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        e: Exception,
        locale: Locale,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(e) { "Unexpected error" }
        return ResponseEntity
            .internalServerError()
            .body(
                ApiResponse.fail(
                    CommonErrorCode.INTERNAL_ERROR,
                    resolveMessage(CommonErrorCode.INTERNAL_ERROR, null, locale),
                ),
            )
    }

    private fun resolveMessage(
        errorCode: ErrorCodeSpec,
        args: Array<Any>?,
        locale: Locale,
    ): String = messageSource.getMessage(errorCode.messageKey, args, errorCode.message, locale) ?: errorCode.message

    companion object {
        /** 클라이언트가 보낸 API 버전 문자열을 응답/로그에 반영할 때의 최대 길이 */
        private const val MAX_VERSION_LENGTH = 32
    }
}
