package com.whatever.caro.auth.internal.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * staging 전용 Swagger Basic Auth SecurityFilterChain.
 *
 * - @Profile("staging") 으로 local/prod 에서는 빈 자체가 로드되지 않음
 */
@Configuration
@Profile("staging")
class SwaggerSecurityConfig {

    @Bean
    @Order(1)
    fun swaggerSecurityFilterChain(
        http: HttpSecurity,
        @Value("\${app.swagger.auth.username:}") username: String,
        @Value("\${app.swagger.auth.password:}") password: String,
    ): SecurityFilterChain {
        require(username.isNotBlank()) { "SWAGGER_USERNAME env var 가 설정되지 않았습니다" }
        require(password.isNotBlank()) { "SWAGGER_PASSWORD env var 가 설정되지 않았습니다" }

        // DelegatingPasswordEncoder 는 해시 알고리즘을 {bcrypt} 같은 prefix 로 식별하므로 prefix 필수.
        val encodedPassword = "{bcrypt}" + BCryptPasswordEncoder().encode(password)
        val userDetailsService = InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(encodedPassword)
                .roles("SWAGGER")
                .build(),
        )

        http.userDetailsService(userDetailsService)
        http {
            securityMatcher(*PublicEndpoints.SWAGGER.toTypedArray())
            csrf { disable() }
            cors { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            httpBasic { realmName = "Caro Swagger (Staging)" }
            authorizeHttpRequests { authorize(anyRequest, authenticated) }
        }
        return http.build()
    }
}
