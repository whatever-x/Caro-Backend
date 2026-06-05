package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.internal.StudyService
import com.whatever.caro.study.internal.web.response.DailyStudySummaryResponse
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
@RequestMapping("/v1/study-sessions")
class StudyController(
    private val clock: Clock,
    private val studyService: StudyService,
) {

    @GetMapping("/daily/summary")
    fun getTodayDailyStudySummary(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
        @RequestParam(value = "deckId", required = true) deckId: Long,
    ): ResponseEntity<DailyStudySummaryResponse> {
        val now = Instant.now(clock)
        val studySession = studyService.getTodaySummary(
            now = now,
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
        )

        return ResponseEntity.ok(studySession.toResponse())
    }
}


private fun TodayStudySessionState.toResponse(): DailyStudySummaryResponse {
    return when (this) {
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
}
