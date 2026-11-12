package com.whatever.caro.auth.internal.filter

import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.CommonErrorCode
import com.whatever.caro.common.response.ErrorCodeSpec
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.LocaleResolver
import tools.jackson.databind.json.JsonMapper

private val customLogger = KotlinLogging.logger {}

@Component
class JwtExceptionFilter(
    private val jsonMapper: JsonMapper,
    private val messageSource: MessageSource,
    private val localeResolver: LocaleResolver,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            filterChain.doFilter(request, response)
        } catch (e: BusinessException) {
            customLogger.warn {
                "Auth Exception: code=${e.errorCode.code}, message=${e.message}, uri=${request.requestURI}"
            }
            writeErrorResponse(request, response, e.errorCode, e.args)
        } catch (e: Exception) {
            customLogger.error { "Unexpected filter exception: ${e.message}, uri=${request.requestURI}" }
            writeErrorResponse(request, response, CommonErrorCode.INTERNAL_ERROR)
        }
    }

    private fun writeErrorResponse(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCodeSpec,
        args: Array<Any>? = null,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val message = messageSource.getMessage(
            errorCode.messageKey,
            args,
            errorCode.message,
            localeResolver.resolveLocale(request),
        )
        jsonMapper.writeValue(response.writer, ApiResponse.fail(errorCode, message))
    }
}
