package com.whatever.caro.auth.internal.filter

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.common.config.LocaleConfig
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper

private val logger = KotlinLogging.logger {}

class JwtExceptionFilterTest :
    DescribeSpec({

        val jsonMapper = JsonMapper.builder().build()
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasename("classpath:messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
        val filter = JwtExceptionFilter(jsonMapper, messageSource, LocaleConfig().localeResolver())

        describe("요청 언어에 따른 필터 오류 메시지") {
            it("영어와 한국어를 구분하고 미지원 언어와 헤더 생략은 영어로 응답한다") {
                for (failure in listOf(InvalidAccessTokenException(), RuntimeException("unexpected"))) {
                    val messages = listOf("en-US", "ko-KR", "fr-FR", null).map { language ->
                        val request = MockHttpServletRequest().apply {
                            language?.let { addHeader("Accept-Language", it) }
                        }
                        val response = MockHttpServletResponse()
                        filter.doFilter(request, response, FilterChain { _, _ -> throw failure })
                        response.status shouldBe if (failure is BusinessException) 401 else 500
                        val error = jsonMapper.readTree(response.contentAsString).path("error")
                        error.path("code").asString() shouldBe if (failure is BusinessException) "A003" else "C999"
                        error.path("message").asString().also {
                            it.isBlank() shouldBe false
                            it.contains("{") shouldBe false
                        }
                    }
                    messages[0] shouldNotBe messages[1]
                    Regex("[가-힣]").containsMatchIn(messages[0]) shouldBe false
                    messages[2] shouldBe messages[0]
                    messages[3] shouldBe messages[0]
                }
            }

            it("BusinessException의 메시지 인자를 보존한다") {
                val request = MockHttpServletRequest().apply { addHeader("Accept-Language", "en") }
                val response = MockHttpServletResponse()
                filter.doFilter(
                    request,
                    response,
                    FilterChain { _, _ ->
                        throw BusinessException(CommonErrorCode.MISSING_HEADER, arrayOf("Device-Id"))
                    },
                )
                val message = jsonMapper.readTree(response.contentAsString).path("error").path("message").asString()
                message.contains("Device-Id") shouldBe true
                message.contains("{") shouldBe false
            }
        }

        describe("doFilterInternal") {
            context("BusinessException 발생 시") {
                it("errorCode의 status와 JSON 에러 응답을 반환한다") {
                    val filterChain = FilterChain { _, _ ->
                        throw InvalidAccessTokenException()
                    }
                    val request = MockHttpServletRequest()
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    response.status shouldBe 401
                    response.contentType shouldBe "application/json;charset=UTF-8"
                    logger.debug { "response: ${response.contentAsString}" }
                }
            }

            context("예상치 못한 Exception 발생 시") {
                it("500 INTERNAL_ERROR 응답을 반환한다") {
                    val filterChain = FilterChain { _, _ ->
                        throw RuntimeException("unexpected")
                    }
                    val request = MockHttpServletRequest()
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    response.status shouldBe CommonErrorCode.INTERNAL_ERROR.status.value()
                    response.contentType shouldBe "application/json;charset=UTF-8"
                    logger.debug { "response: ${response.contentAsString}" }
                }
            }

            context("예외가 없을 때") {
                it("filterChain이 정상 통과한다") {
                    val filterChain = mockk<FilterChain>(relaxed = true)
                    val request = MockHttpServletRequest()
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    response.status shouldBe 200
                    verify { filterChain.doFilter(request, response) }
                }
            }
        }
    })
