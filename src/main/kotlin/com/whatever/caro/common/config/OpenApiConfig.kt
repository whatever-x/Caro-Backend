package com.whatever.caro.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!prod")
class OpenApiConfig {

    @Bean
    fun caroOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Caro Backend API")
                    .description("Flashcard backend REST API")
                    .version("v1"),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_SCHEME_NAME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT Access Token"),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME_NAME))

    companion object {
        const val BEARER_SCHEME_NAME = "bearerAuth"
    }
}
