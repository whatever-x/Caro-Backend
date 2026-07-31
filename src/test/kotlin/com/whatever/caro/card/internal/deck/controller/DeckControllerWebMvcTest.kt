package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.card.internal.deck.service.DeckService
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

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
