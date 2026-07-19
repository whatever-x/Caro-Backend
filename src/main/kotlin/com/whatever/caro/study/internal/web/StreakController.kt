package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.study.internal.streak.StreakService
import com.whatever.caro.study.internal.web.response.StreakResponse
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
@RequestMapping("/v1/streaks")
class StreakController(
    private val clock: Clock,
    private val streakService: StreakService,
) {

    @Operation(
        summary = "현재 streak 조회",
        description = "오늘이 휴식일(모든 학습 활성 덱의 due=0)이면 휴식일을 기록한 뒤 현재 streak을 반환한다.",
    )
    @GetMapping
    fun getStreak(
        @RequestHeader("Client-Timezone") timezone: ZoneId,
    ): ResponseEntity<ApiResponse<StreakResponse>> {
        val now = Instant.now(clock)
        val currentStreak = streakService.getStreak(
            userId = SecurityUtil.currentUser().userId,
            now = now,
            timezone = timezone,
            dayCutoffHour = 0,
        )
        return ResponseEntity.ok(ApiResponse.ok(StreakResponse(currentStreak = currentStreak)))
    }

    @PostMapping("/sync")
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
