package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.bff.internal.DailyStudyView
import com.whatever.caro.bff.internal.StudyBFFService
import com.whatever.caro.bff.internal.web.request.StartDailyStudyRequest
import com.whatever.caro.bff.internal.web.response.DailyStudyResponse
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.common.web.idempotency.Idempotent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Tag(name = "StudySession", description = "일일학습 세션 / 평가")
@RestController
@RequestMapping("/study-sessions")
class StudyBFFController(
    private val clock: Clock,
    private val studyBFFService: StudyBFFService,
) {

    @Operation(
        summary = "일일학습 시작 / 재개",
        description = """
        일일학습을 시작하거나 재개한다.

        중복 요청 방지를 위해 UUIDv4인 `Idempotency-Key` 헤더를 포함해야한다.
        이 endpoint는 멱등하므로 안전한 재시도가 가능하다.
        """,
    )
    @Idempotent
    @PostMapping("/daily", version = "1.0")
    fun startDailyStudy(
        @RequestHeader("Idempotency-Key", required = true) idempotencyKey: String,
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
        @RequestBody req: StartDailyStudyRequest,
    ): ResponseEntity<ApiResponse<DailyStudyResponse>> {
        val now = Instant.now(clock)
        val userId = SecurityUtil.currentUser().userId

        val result = studyBFFService.startOrResumeDailyStudy(
            now = now,
            userId = userId,
            deckId = req.deckId,
            timezone = timezone,
        )

        return ResponseEntity.ok(ApiResponse.ok(result.toResponse()))
    }
}

private fun DailyStudyView.toResponse(): DailyStudyResponse =
    when (this) {
        is DailyStudyView.InProgressDto -> DailyStudyResponse.InProgress(
            sessionId = sessionId,
            studiedCardCount = studiedCardCount,
            totalCardCount = totalCardCount,
            cards = cards,
        )

        is DailyStudyView.CompletedDto -> DailyStudyResponse.Completed(
            sessionId = sessionId,
            studiedCardCount = studiedCardCount,
            totalCardCount = totalCardCount,
        )

        is DailyStudyView.RestDayDto -> DailyStudyResponse.RestDay
    }
