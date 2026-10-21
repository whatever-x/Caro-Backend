package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.bff.internal.InvalidReportPeriodException
import com.whatever.caro.bff.internal.ReportBucketUnit
import com.whatever.caro.bff.internal.ReportPageView
import com.whatever.caro.bff.internal.ReportRange
import com.whatever.caro.bff.internal.ReportScope
import com.whatever.caro.bff.internal.ReportSummaryView
import com.whatever.caro.bff.internal.ReportTargetNotFoundException
import com.whatever.caro.bff.internal.StudyReportBFFService
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import com.whatever.caro.study.Rating
import com.whatever.caro.study.StudyReportCountsDto
import com.whatever.caro.study.StudyReportRatingCountsDto
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
import java.time.LocalDate
import java.time.ZoneId

@WebMvcTest(
    controllers = [StudyReportBFFController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class StudyReportBFFControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var studyReportBFFService: StudyReportBFFService

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
        val fixedNow = Instant.parse("2026-10-21T01:00:00Z")
        val kst = ZoneId.of("Asia/Seoul")
        val start = LocalDate.parse("2026-10-15")
        val end = LocalDate.parse("2026-10-21")
        val counts = StudyReportCountsDto(144, 96, 6, StudyReportRatingCountsDto(82, 38, 24))

        fun summary(
            lifetime: StudyReportCountsDto?,
        ) = ReportSummaryView(
            scope = ReportScope(null, null, false),
            range = ReportRange(start, end, ReportBucketUnit.DAY),
            lifetime = lifetime,
            selected = counts,
            buckets = emptyList(),
            restDates = listOf(LocalDate.parse("2026-10-17")),
        )

        beforeTest {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE"),
                    null,
                    emptyList(),
                )
            given(clock.instant()).willReturn(fixedNow)
        }

        afterTest {
            SecurityContextHolder.clearContext()
        }

        describe("GET /study-reports/summary") {
            it("헤더의 시간대와 날짜를 서비스에 전달하고 ISO 날짜로 응답한다") {
                given(studyReportBFFService.getSummary(fixedNow, kst, 1L, null, start, end)).willReturn(summary(counts))

                mockMvc.get("/study-reports/summary") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("startDate", "2026-10-15")
                    param("endDate", "2026-10-21")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.range.startDate") { value("2026-10-15") }
                    jsonPath("$.data.range.bucketUnit") { value("DAY") }
                    jsonPath("$.data.lifetime.reviewCount") { value(144) }
                    jsonPath("$.data.selected.ratings.again") { value(24) }
                    jsonPath("$.data.restDates[0]") { value("2026-10-17") }
                    jsonPath("$.data.scope.deckName") { value(null) }
                }

                verify(studyReportBFFService).getSummary(fixedNow, kst, 1L, null, start, end)
            }

            it("덱을 골라 lifetime이 없으면 응답에서 그 필드를 뺀다") {
                given(studyReportBFFService.getSummary(fixedNow, kst, 1L, 3L, start, end)).willReturn(summary(null))

                mockMvc.get("/study-reports/summary") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("deckId", "3")
                    param("startDate", "2026-10-15")
                    param("endDate", "2026-10-21")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.lifetime") { doesNotExist() }
                }
            }

            it("API-Version 헤더가 없으면 기본 1.0으로 처리한다") {
                given(studyReportBFFService.getSummary(fixedNow, kst, 1L, null, null, null)).willReturn(summary(counts))

                mockMvc.get("/study-reports/summary") {
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                }.andExpect {
                    status { isOk() }
                }
            }

            it("Client-Timezone 헤더가 없으면 400(C007)이다") {
                mockMvc.get("/study-reports/summary") {
                    header(API_VERSION_HEADER, API_VERSION)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("C007") }
                }
            }

            it("날짜 형식이 틀리면 400(C006)이다") {
                mockMvc.get("/study-reports/summary") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("startDate", "2026/10/15")
                    param("endDate", "2026-10-21")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("C006") }
                }
            }

            it("서비스가 기간 오류를 던지면 400(R001)이다") {
                given(studyReportBFFService.getSummary(fixedNow, kst, 1L, null, start, null))
                    .willThrow(InvalidReportPeriodException("one side"))

                mockMvc.get("/study-reports/summary") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("startDate", "2026-10-15")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("R001") }
                }
            }

            it("접근할 수 없는 덱은 404(R003)이고 한국어와 영어 문구가 다르다") {
                given(studyReportBFFService.getSummary(fixedNow, kst, 1L, 77L, null, null))
                    .willThrow(ReportTargetNotFoundException("no deck"))

                val messages = listOf("ko", "en").map { lang ->
                    mockMvc.get("/study-reports/summary") {
                        header(API_VERSION_HEADER, API_VERSION)
                        header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                        header("Accept-Language", lang)
                        param("deckId", "77")
                    }.andExpect {
                        status { isNotFound() }
                        jsonPath("$.error.code") { value("R003") }
                    }.andReturn().response.getContentAsString(Charsets.UTF_8)
                }

                check(messages[0] != messages[1]) { "ko/en messages should differ" }
                check(messages.none { it.contains("{") && it.contains("error.report") }) { "message key should be resolved" }
            }
        }

        describe("GET /study-reports/cards") {
            it("deckId가 없으면 400(C005)이다") {
                mockMvc.get("/study-reports/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("C005") }
                }
            }

            it("평가 필터 값이 틀리면 400(C006)이다") {
                mockMvc.get("/study-reports/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("deckId", "1")
                    param("lastRating", "GOOD")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("C006") }
                }
            }

            it("평가 필터와 페이지 크기를 서비스에 전달한다") {
                given(studyReportBFFService.getCards(fixedNow, kst, 1L, 1L, null, null, Rating.AGAIN, null, 50))
                    .willReturn(ReportPageView(emptyList(), null))

                mockMvc.get("/study-reports/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header(CLIENT_TIMEZONE_HEADER, "Asia/Seoul")
                    param("deckId", "1")
                    param("lastRating", "AGAIN")
                    param("limit", "50")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items") { isEmpty() }
                    jsonPath("$.data.nextCursor") { value(null) }
                }
            }
        }

        describe("GET /study-reports/decks") {
            it("페이지 크기가 1~100을 벗어나면 400(C001)이다") {
                listOf("0", "101").forEach { limit ->
                    mockMvc.get("/study-reports/decks") {
                        header(API_VERSION_HEADER, API_VERSION)
                        param("limit", limit)
                    }.andExpect {
                        status { isBadRequest() }
                        jsonPath("$.error.code") { value("C001") }
                    }
                }
            }

            it("기본 페이지 크기는 20이다") {
                given(studyReportBFFService.getDeckOptions(1L, null, 20)).willReturn(ReportPageView(emptyList(), null))

                mockMvc.get("/study-reports/decks") {
                    header(API_VERSION_HEADER, API_VERSION)
                }.andExpect {
                    status { isOk() }
                }

                verify(studyReportBFFService).getDeckOptions(1L, null, 20)
            }
        }
    }

    companion object {
        private const val API_VERSION_HEADER = "API-Version"
        private const val API_VERSION = "1.0"
        private const val CLIENT_TIMEZONE_HEADER = "Client-Timezone"
    }
}
