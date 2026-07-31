package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.study.internal.streak.StreakService
import com.whatever.caro.study.internal.streak.StreakStatusResult
import com.whatever.caro.study.internal.web.response.StreakResponse
import com.whatever.caro.study.internal.web.response.StreakStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Tag(name = "Streak", description = "연속 학습(streak)")
@RestController
@RequestMapping("/streaks")
class StreakController(
    private val clock: Clock,
    private val streakService: StreakService,
) {

    @Operation(
        summary = "현재 streak 조회",
        description = "오늘이 휴식일(모든 학습 활성 덱의 due=0)이면 휴식일을 기록한 뒤 현재 streak을 반환한다.",
    )
    @GetMapping(version = "1.0")
    fun getStreak(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
    ): ResponseEntity<ApiResponse<StreakResponse>> {
        val now = Instant.now(clock)
        val streakStatus = streakService.getStreak(
            userId = SecurityUtil.currentUser().userId,
            now = now,
            timezone = timezone,
            dayCutoffHour = 0,
        )

        val response = when (streakStatus) {
            is StreakStatusResult.Active -> StreakResponse(
                status = StreakStatus.ACTIVE,
                currentStreak = streakStatus.currentStreak,
            )

            StreakStatusResult.NotStarted -> StreakResponse(
                status = StreakStatus.NOT_STARTED,
                currentStreak = 0,
            )

            StreakStatusResult.Broken -> StreakResponse(
                status = StreakStatus.BROKEN,
                currentStreak = 0,
            )
        }
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @Operation(
        summary = "streak 동기화",
        description = """
        오늘까지의 학습 이력을 기준으로 휴식일 여부를 확인한 뒤 streak을 동기화한다.
        오프라인 학습 후 재접속 시 클라이언트가 호출해 서버 streak을 최신 상태로 맞추는 용도이다.
        """,
    )
    @PostMapping("/sync", version = "1.0")
    fun syncStreak(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
    ): ResponseEntity<ApiResponse<Unit>> {
        val now = Instant.now(clock)
        streakService.syncWithRestDayCheck(
            userId = SecurityUtil.currentUser().userId,
            now = now,
            timezone = timezone,
            dayCutoffHour = 0,
        )
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }
}
