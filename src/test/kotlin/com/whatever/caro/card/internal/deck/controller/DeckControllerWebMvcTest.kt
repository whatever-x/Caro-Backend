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

        describe("POST /v1/decks - @Valid 검증") {
            it("name이 blank면 400을 반환한다") {
                mockMvc.post("/v1/decks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "", "description": "설명"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            it("description이 blank면 400을 반환한다") {
                mockMvc.post("/v1/decks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "새 덱", "description": ""}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }
        }

        describe("PATCH /v1/decks/{deckId} - @Valid 검증") {
            it("name이 blank면 400을 반환한다") {
                mockMvc.patch("/v1/decks/1") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "", "description": "새 설명"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            it("description이 blank면 400을 반환한다") {
                mockMvc.patch("/v1/decks/1") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "새 이름", "description": ""}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }
        }
    }
}
