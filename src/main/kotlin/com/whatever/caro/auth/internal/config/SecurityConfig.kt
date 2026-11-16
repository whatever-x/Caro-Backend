package com.whatever.caro.auth.internal.config

import com.whatever.caro.auth.exception.AuthErrorCode
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.ErrorCodeSpec
import com.whatever.caro.common.web.filter.RequestResponseCachingFilter
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.LocaleResolver
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val requestResponseCachingFilter: RequestResponseCachingFilter,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtExceptionFilter: JwtExceptionFilter,
    private val jsonMapper: JsonMapper,
    private val messageSource: MessageSource,
    private val localeResolver: LocaleResolver,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http {
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            csrf { disable() }
            cors { disable() }

            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }

            authorizeHttpRequests {
                // staging 에서는 SwaggerSecurityConfig가 우선시되어 Basic Auth 필요
                PublicEndpoints.PATTERNS.forEach { authorize(it, permitAll) }
                authorize("/auth/complete-registration", hasRole("SUSPENDED"))
                authorize("/nicknames/**", hasAnyRole("SUSPENDED", "ACTIVE"))
                authorize("/users/nicknames/**", hasAnyRole("SUSPENDED", "ACTIVE"))
                authorize("/auth/logout", hasAnyRole("SUSPENDED", "ACTIVE"))
                authorize(anyRequest, hasRole("ACTIVE"))
            }

            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtExceptionFilter)
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
            addFilterAfter<JwtAuthenticationFilter>(requestResponseCachingFilter)

            exceptionHandling {
                authenticationEntryPoint = customAuthenticationEntryPoint()
                accessDeniedHandler = customAccessDeniedHandler()
            }
        }
        return http.build()
    }

    private fun customAuthenticationEntryPoint() =
        AuthenticationEntryPoint { request, response, _ ->
            writeErrorResponse(request, response, AuthErrorCode.UNAUTHORIZED)
        }

    private fun customAccessDeniedHandler() =
        AccessDeniedHandler { request, response, _ ->
            writeErrorResponse(request, response, AuthErrorCode.ACCESS_DENIED)
        }

    private fun writeErrorResponse(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCodeSpec,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val message = messageSource.getMessage(
            errorCode.messageKey,
            null,
            errorCode.message,
            localeResolver.resolveLocale(request),
        )
        jsonMapper.writeValue(response.writer, ApiResponse.fail(errorCode, message))
    }
}

@Configuration
class SecurityFilterRegistrationConfig {
    @Bean
    fun jwtExceptionFilterRegistration(
        filter: JwtExceptionFilter,
    ): FilterRegistrationBean<JwtExceptionFilter> = disableFilterRegistration(filter)

    @Bean
    fun jwtAuthenticationFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> = disableFilterRegistration(filter)

    @Bean
    fun requestResponseCachingFilterRegistration(
        filter: RequestResponseCachingFilter,
    ): FilterRegistrationBean<RequestResponseCachingFilter> = disableFilterRegistration(filter)

    private fun <T : Filter> disableFilterRegistration(
        filter: T,
    ): FilterRegistrationBean<T> = FilterRegistrationBean(filter).apply { isEnabled = false }
}
