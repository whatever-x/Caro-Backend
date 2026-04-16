package com.whatever.caro.auth.internal.filter

import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.CommonErrorCode
import com.whatever.caro.common.response.ErrorCodeSpec
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

private val customLogger = KotlinLogging.logger {}

@Component
class JwtExceptionFilter(
    private val jsonMapper: JsonMapper,
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
            writeErrorResponse(response, e.errorCode)
        } catch (e: Exception) {
            customLogger.error { "Unexpected filter exception: ${e.message}, uri=${request.requestURI}" }
            writeErrorResponse(response, CommonErrorCode.INTERNAL_ERROR)
        }
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        errorCode: ErrorCodeSpec,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        jsonMapper.writeValue(response.writer, ApiResponse.fail(errorCode))
    }
}
