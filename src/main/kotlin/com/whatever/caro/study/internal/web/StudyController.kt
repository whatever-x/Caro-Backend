package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.study.internal.StudyService
import com.whatever.caro.study.internal.web.response.TodayStudySummaryResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RestController
@RequestMapping("/v1/study-sessions")
class StudyController(
    private val clock: Clock,
    private val studyService: StudyService,
) {

    @GetMapping("/daily/summary")
    fun getTodayStudySummary(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
        @RequestParam(value = "deckId", required = true) deckId: Long,
    ): ResponseEntity<TodayStudySummaryResponse> {
        val now = Instant.now(clock)
        val studySession = studyService.getTodaySummary(
            now = now,
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
        )

        return ResponseEntity.ok(TodayStudySummaryResponse.from(studySession))
    }
}
