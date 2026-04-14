package com.whatever.caro.auth.internal.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

private val customLogger = KotlinLogging.logger {}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    private val pathMatcher = AntPathMatcher()

    override fun shouldNotFilter(
        request: HttpServletRequest,
    ): Boolean {
        val path = request.requestURI
        return SKIP_LOGGING_PATTERNS.any { pathMatcher.match(it, path) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startTime = System.currentTimeMillis()

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logRequest(request, response, duration)
        }
    }

    private fun logRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        duration: Long,
    ) {
        val method = request.method
        val uri = request.requestURI
        val query = request.queryString?.let { "?${sanitize(it)}" }.orEmpty()
        val status = response.status
        val headers = maskHeaders(request)

        val message = "$method ${uri}$query | status=$status | ${duration}ms | headers=$headers"

        when {
            status >= 500 -> customLogger.error { message }
            status >= 400 -> customLogger.warn { message }
            else -> customLogger.info { message }
        }
    }

    private fun maskHeaders(
        request: HttpServletRequest,
    ): Map<String, String> =
        request.headerNames.toList().associateWith { name ->
            if (name.lowercase() in MASKING_TARGET_HEADERS) {
                MASKED_STRING
            } else {
                sanitize(request.getHeader(name))
            }
        }

    private fun sanitize(
        str: String,
    ): String = str.replace(CRLF_TAB_REGEX, "_")

    companion object {
        private val MASKING_TARGET_HEADERS = setOf("authorization", "cookie", "set-cookie")
        private val MASKED_STRING = "***"
        private val CRLF_TAB_REGEX = Regex("[\\r\\n\\t]")
        private val SKIP_LOGGING_PATTERNS = listOf("/actuator/**")
    }
}
