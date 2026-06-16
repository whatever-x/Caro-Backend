package com.whatever.caro.common.exception

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.CommonErrorCode
import com.whatever.caro.common.response.ErrorCodeSpec
import com.whatever.caro.common.response.FieldError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
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
}
