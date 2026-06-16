package com.whatever.caro.auth.internal.config

import com.whatever.caro.auth.exception.AuthErrorCode
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.response.ErrorCodeSpec
import com.whatever.caro.common.web.filter.RequestResponseCachingFilter
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
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
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val requestResponseCachingFilter: RequestResponseCachingFilter,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtExceptionFilter: JwtExceptionFilter,
    private val jsonMapper: JsonMapper,
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
                authorize("/v1/auth/complete-registration", hasRole("SUSPENDED"))
                authorize("/v1/nicknames/**", hasRole("SUSPENDED"))
                authorize("/v1/users/nickname/**", hasRole("SUSPENDED"))
                authorize("/v1/auth/logout", authenticated)
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
        AuthenticationEntryPoint { _, response, _ ->
            writeErrorResponse(response, AuthErrorCode.UNAUTHORIZED)
        }

    private fun customAccessDeniedHandler() =
        AccessDeniedHandler { _, response, _ ->
            writeErrorResponse(response, AuthErrorCode.ACCESS_DENIED)
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
