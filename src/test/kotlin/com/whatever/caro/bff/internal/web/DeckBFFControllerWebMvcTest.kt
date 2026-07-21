package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.bff.internal.CardSortType
import com.whatever.caro.bff.internal.DeckBFFService
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Clock

@WebMvcTest(
    controllers = [DeckBFFController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class DeckBFFControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var deckBFFService: DeckBFFService

    @MockitoBean
    lateinit var clock: Clock

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
        beforeTest {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE"),
                    null,
                    emptyList(),
                )
        }

        afterTest {
            SecurityContextHolder.clearContext()
        }

        describe("GET /v2/decks/{deckId}/cards - sortType 바인딩") {
            it("sortType을 생략하면 기본값 CREATED로 서비스에 전달한다") {
                given(deckBFFService.getCardsWithLearningState(1L, 1L, CardSortType.CREATED))
                    .willReturn(emptyList())

                mockMvc.get("/v2/decks/1/cards").andExpect {
                    status { isOk() }
                }

                verify(deckBFFService).getCardsWithLearningState(
                    userId = 1L,
                    deckId = 1L,
                    sortType = CardSortType.CREATED,
                )
            }

            it("요청으로 들어온 sortType=LAST_REVIEWED를 그대로 서비스에 전달한다") {
                given(deckBFFService.getCardsWithLearningState(1L, 1L, CardSortType.LAST_REVIEWED))
                    .willReturn(emptyList())

                mockMvc.get("/v2/decks/1/cards") {
                    param("sortType", "LAST_REVIEWED")
                }.andExpect {
                    status { isOk() }
                }

                verify(deckBFFService).getCardsWithLearningState(
                    userId = 1L,
                    deckId = 1L,
                    sortType = CardSortType.LAST_REVIEWED,
                )
            }

            it("유효하지 않은 sortType이면 400을 반환한다") {
                mockMvc.get("/v2/decks/1/cards") {
                    param("sortType", "INVALID")
                }.andExpect {
                    status { isBadRequest() }
                }
            }
        }
    }
}
