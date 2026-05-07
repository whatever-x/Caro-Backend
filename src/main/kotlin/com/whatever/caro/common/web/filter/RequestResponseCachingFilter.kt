package com.whatever.caro.common.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class RequestResponseCachingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cachedRequest = CachedBodyHttpServletRequest(request)
        val cachedResponse = ContentCachingResponseWrapper(response)

        try {
            filterChain.doFilter(cachedRequest, cachedResponse)
        } finally {
            cachedResponse.copyBodyToResponse()
        }
    }
}
