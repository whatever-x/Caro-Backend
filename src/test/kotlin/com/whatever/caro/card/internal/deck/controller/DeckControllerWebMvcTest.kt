package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

@WebMvcTest(
    controllers = [DeckController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class DeckControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var deckService: DeckService

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean
    lateinit var jwtExceptionFilter: JwtExceptionFilter

    @MockitoBean
    lateinit var requestResponseLoggingFilter: RequestResponseLoggingFilter

    @MockitoBean
    lateinit var idempotencyRepository: IdempotencyRepository

    @MockitoBean
    lateinit var idempotencyProperties: IdempotencyProperties

    init {
        beforeEach {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE"),
                    null,
                    emptyList(),
                )
        }

        afterEach {
            SecurityContextHolder.clearContext()
        }

        describe("POST /decks - 검증 메시지 언어") {
            it("필드 오류도 요청 언어를 따르며 미지원 언어와 헤더 생략은 영어를 사용한다") {
                val mapper = JsonMapper.builder().build()
                val messages = listOf("en-US", "ko-KR", "ja-JP", null).map { language ->
                    val response = mockMvc.post("/decks") {
                        header(API_VERSION_HEADER, API_VERSION)
                        language?.let { header("Accept-Language", it) }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name":"","description":""}"""
                    }.andExpect {
                        status { isBadRequest() }
                        jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                    }.andReturn().response
                    val error = mapper.readTree(response.contentAsByteArray).path("error")
                    val fieldErrors = error.path("fieldErrors")
                    val texts = listOf(error.path("message").asString()) +
                        fieldErrors.values().map { it.path("message").asString() }.sorted()
                    texts.size shouldBe 3
                    texts.forEach {
                        it.isBlank() shouldBe false
                        it.contains("{") shouldBe false
                    }
                    texts
                }
                messages[0].zip(messages[1]).forEach { (en, ko) ->
                    en shouldNotBe ko
                    Regex("[가-힣]").containsMatchIn(en) shouldBe false
                }
                messages[2] shouldBe messages[0]
                messages[3] shouldBe messages[0]
            }
        }

        describe("POST /decks - @Valid 검증") {
            it("name이 blank면 400을 반환한다") {
                mockMvc.post("/decks") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "", "description": "설명"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("description이 blank면 400을 반환한다") {
                mockMvc.post("/decks") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "새 덱", "description": ""}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }

        describe("PATCH /decks/{deckId} - @Valid 검증") {
            it("name이 blank면 400을 반환한다") {
                mockMvc.patch("/decks/1") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "", "description": "새 설명"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("description이 blank면 400을 반환한다") {
                mockMvc.patch("/decks/1") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "새 이름", "description": ""}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }
    }

    companion object {
        private const val API_VERSION_HEADER = "API-Version"
        private const val API_VERSION = "1.0"
        private const val INVALID_INPUT_CODE = "C001"
    }
}
