package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.bff.internal.CardSortType
import com.whatever.caro.bff.internal.DeckBFFService
import com.whatever.caro.bff.internal.DeckListItem
import com.whatever.caro.bff.internal.StudySessionProgress
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import com.whatever.caro.study.TodaySummaryState
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
import java.time.Instant
import java.time.ZoneId

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

        describe("GET /decks/{deckId}/cards (v2) - sortType 바인딩") {
            it("sortType을 생략하면 기본값 CREATED로 서비스에 전달한다") {
                given(deckBFFService.getCardsWithLearningState(1L, 1L, CardSortType.CREATED))
                    .willReturn(emptyList())

                mockMvc.get("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                }.andExpect {
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

                mockMvc.get("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
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
                mockMvc.get("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    param("sortType", "INVALID")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(TYPE_MISMATCH_CODE) }
                }
            }
        }

        describe("GET /decks/{deckId} (v1) - 단건 덱 상세 조회") {
            it("PathVariable deckId와 인증 userId를 서비스에 전달하고 200과 응답을 반환한다") {
                val fixedNow = Instant.parse("2026-08-07T00:00:00Z")
                val timezone = ZoneId.of("Asia/Seoul")
                given(clock.instant()).willReturn(fixedNow)

                val item = DeckListItem(
                    deckId = 1L,
                    name = "내 덱",
                    description = "설명",
                    cardCount = 5,
                    progress = StudySessionProgress(
                        state = TodaySummaryState.REST_DAY,
                        sessionId = null,
                        studiedCardCount = 0,
                        totalCardCount = 0,
                    ),
                )
                given(deckBFFService.getDeckByDeckId(fixedNow, timezone, 1L, 1L)).willReturn(item)

                mockMvc.get("/decks/1") {
                    header(API_VERSION_HEADER, DECK_DETAIL_API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.deckId") { value(1) }
                    jsonPath("$.data.name") { value("내 덱") }
                }

                verify(deckBFFService).getDeckByDeckId(fixedNow, timezone, 1L, 1L)
            }
        }
    }

    companion object {
        private const val API_VERSION_HEADER = "API-Version"
        private const val API_VERSION = "2.0"
        private const val DECK_DETAIL_API_VERSION = "1.0"
        private const val CLIENT_TIMEZONE_HEADER = "Client-Timezone"
        private const val TYPE_MISMATCH_CODE = "C006"
    }
}
