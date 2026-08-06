package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@WebMvcTest(
    controllers = [CardController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var cardService: CardService

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

        describe("POST /decks/{deckId}/cards - @Valid 검증") {
            it("items 가 비어있으면 400 을 반환한다") {
                mockMvc.post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"items": []}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("item 의 fields 가 비어있으면 400 을 반환한다") {
                mockMvc.post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"items": [{"cardType": "BASIC", "fields": {}}]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }

        describe("PATCH /cards/{id} - @Valid 검증") {
            it("fields 가 비어있으면 400 을 반환한다") {
                mockMvc.patch("/cards/1") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"fields": {}}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }

        describe("DELETE /cards - @Valid 검증") {
            it("cardIds 가 비어있으면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": []}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("cardIds 가 1000개를 초과하면 400 을 반환한다") {
                val ids = (1..1001).joinToString(",")
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [$ids]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("cardIds 에 0 이하가 섞이면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [1, 0]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("Client-Timezone 헤더가 없으면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [1]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(MISSING_HEADER_CODE) }
                }
            }
        }
    }

    companion object {
        private const val API_VERSION_HEADER = "API-Version"
        private const val API_VERSION = "1.0"
        private const val INVALID_INPUT_CODE = "C001"
        private const val MISSING_HEADER_CODE = "C007"
    }
}
