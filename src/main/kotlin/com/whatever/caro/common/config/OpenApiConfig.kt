package com.whatever.caro.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springdoc.core.filters.OpenApiMethodFilter
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import java.lang.reflect.Method

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
    fun acceptLanguageHeaderCustomizer(): GlobalOperationCustomizer =
        GlobalOperationCustomizer { operation, _ ->
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

    @Bean
    fun apiGroupAll(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("all-version")
            .displayName("All API version")
            .addOpenApiMethodFilter { method: Method ->
                val deprecated = AnnotatedElementUtils.findMergedAnnotation(method, Deprecated::class.java)
                deprecated == null
            }
            .build()

    @Bean
    fun apiGroupV1(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("version-1")
            .displayName("API version 1")
            .addOpenApiMethodFilter(versionMethodFilter("1.0"))
            .build()

    @Bean
    fun apiGroupV2(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("version-2")
            .displayName("API version 2")
            .addOpenApiMethodFilter(versionMethodFilter("2.0"))
            .build()

    private fun versionMethodFilter(
        targetVersion: String,
    ): OpenApiMethodFilter =
        OpenApiMethodFilter { method: Method ->
            val requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
            requestMapping?.version == targetVersion
        }

    companion object {
        const val BEARER_SCHEME_NAME = "bearerAuth"
    }
}
