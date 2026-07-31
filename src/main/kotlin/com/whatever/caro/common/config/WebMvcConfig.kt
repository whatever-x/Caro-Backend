package com.whatever.caro.common.config

import com.whatever.caro.common.web.idempotency.IdempotencyInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val idempotencyInterceptor: IdempotencyInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(
        registry: InterceptorRegistry,
    ) {
        registry.addInterceptor(idempotencyInterceptor)
    }

    override fun configureApiVersioning(
        configurer: ApiVersionConfigurer,
    ) {
        configurer.useRequestHeader("API-Version")
    }
}
