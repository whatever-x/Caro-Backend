package com.whatever.caro.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
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

    @Bean
    fun acceptLanguageHeaderCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, _ ->
            operation.apply {
                addParametersItem(
                    Parameter()
                        .`in`("header")
                        .name("Accept-Language")
                        .description("응답 메시지 로케일 (BCP 47). 예: ko-KR, en-US, ja. 미지정 시 영어 메시지로 응답.")
                        .required(true)
                        .example("ko-KR")
                        .schema(StringSchema()._default("ko-KR")),
                )
                addParametersItem(
                    Parameter()
                        .`in`("header")
                        .name("Client-Timezone")
                        .description("클라이언트의 IANA Time Zone Database ID. 예: Asia/Seoul, America/New_York")
                        .required(true)
                        .example("Asia/Seoul")
                        .schema(StringSchema()._default("Asia/Seoul")),
                )
            }
        }

    companion object {
        const val BEARER_SCHEME_NAME = "bearerAuth"
    }
}
