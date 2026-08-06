package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.internal.StudyService
import com.whatever.caro.study.internal.web.response.DailyStudySummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Tag(name = "StudySession", description = "일일학습 세션 / 평가")
@RestController
@RequestMapping("/study-sessions")
class StudyController(
    private val clock: Clock,
    private val studyService: StudyService,
) {

    @Operation(
        summary = "오늘 일일학습 요약 조회",
        description = """
        특정 덱의 오늘 일일학습 상태를 조회한다.
        상태에 따라 미시작(NotStarted) / 진행중(InProgress) / 완료(Completed) / 휴식일(RestDay)로 응답하며,
        각 상태에서 학습한 카드 수와 오늘 목표 카드 수를 함께 제공한다.
        """,
    )
    @GetMapping("/daily/summary", version = "1.0")
    fun getTodayDailyStudySummary(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
        @RequestParam(value = "deckId", required = true) deckId: Long,
    ): ResponseEntity<ApiResponse<DailyStudySummaryResponse>> {
        val now = Instant.now(clock)
        val studySession = studyService.getTodaySummary(
            now = now,
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
        )

        return ResponseEntity.ok(ApiResponse.ok(studySession.toResponse()))
    }
}

private fun TodayStudySessionState.toResponse(): DailyStudySummaryResponse =
    when (this) {
        is TodayStudySessionState.NotStarted -> DailyStudySummaryResponse.NotStarted(
            studiedCardCount = 0,
            totalCardCount = this.pool.newCount + this.pool.reviewCount,
        )

        is TodayStudySessionState.InProgress -> DailyStudySummaryResponse.InProgress(
            sessionId = this.session.sessionId,
            studiedCardCount = this.session.newCardsStudied + this.session.reviewCardsStudied,
            totalCardCount = this.session.estimatedTotal,
        )

        is TodayStudySessionState.Completed -> DailyStudySummaryResponse.Completed(
            sessionId = this.session.sessionId,
            studiedCardCount = this.session.newCardsStudied + this.session.reviewCardsStudied,
            totalCardCount = this.session.estimatedTotal,
        )

        is TodayStudySessionState.RestDay -> DailyStudySummaryResponse.RestDay
    }
