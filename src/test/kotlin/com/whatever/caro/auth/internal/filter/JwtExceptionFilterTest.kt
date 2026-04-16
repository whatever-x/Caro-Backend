package com.whatever.caro.auth.internal.filter

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.common.response.CommonErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper

private val logger = KotlinLogging.logger {}

class JwtExceptionFilterTest :
    DescribeSpec({

        val jsonMapper = JsonMapper.builder().build()
        val filter = JwtExceptionFilter(jsonMapper)

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
