package com.whatever.caro.auth.internal.filter

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.auth.internal.config.PublicEndpoints
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.auth.internal.token.TokenBlacklistRepository
import com.whatever.caro.auth.internal.token.TokenClaims
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

private val customLogger = KotlinLogging.logger {}

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(
        request: HttpServletRequest,
    ): Boolean {
        val path = request.requestURI
        return PublicEndpoints.PATTERNS.any { pathMatcher.match(it, path) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractBearerPrefix(request)

        if (token != null) {
            val claims = try {
                jwtTokenProvider.parseAccessToken(token)
            } catch (e: ExpiredJwtException) {
                throw InvalidAccessTokenException("만료된 토큰입니다", e)
            } catch (e: JwtException) {
                throw InvalidAccessTokenException("유효하지 않은 토큰입니다", e)
            }

            if (tokenBlacklistRepository.isBlacklisted(claims.jti)) {
                customLogger.warn { "Blacklisted token used: userId=${claims.userId}, jti=${claims.jti}" }
                throw InvalidAccessTokenException("무효화된 토큰입니다")
            }

            val context = SecurityContextHolder.createEmptyContext().apply {
                val authentication = getAuthentication(claims)
                this.authentication = authentication
            }
            SecurityContextHolder.setContext(context)
        }

        filterChain.doFilter(request, response)
    }

    private fun getAuthentication(
        claims: TokenClaims,
    ): UsernamePasswordAuthenticationToken {
        val authUser = AuthUser(userId = claims.userId, jti = claims.jti, status = claims.status)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.status}"))
        val authentication = UsernamePasswordAuthenticationToken(authUser, null, authorities)
        return authentication
    }

    private fun extractBearerPrefix(
        request: HttpServletRequest,
    ): String? {
        val header = request.getHeader(AUTHORIZATION_HEADER) ?: return null

        if (!header.startsWith(BEARER_PREFIX)) {
            return null
        }

        val accessToken = header.removePrefix(BEARER_PREFIX)
        if (accessToken.isBlank()) {
            return null
        }

        return accessToken
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val pathMatcher = AntPathMatcher()
    }
}
