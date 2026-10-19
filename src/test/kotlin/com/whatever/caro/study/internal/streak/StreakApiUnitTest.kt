package com.whatever.caro.study.internal.streak

import com.whatever.caro.study.StreakAtRiskDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StreakApiUnitTest :
    DescribeSpec({
        val streakStateRepository = mockk<StreakStateRepository>()
        val restDayCheckService = mockk<RestDayCheckService>()
        val streakService = StreakService(
            streakStateRepository = streakStateRepository,
            studyDayRepository = mockk(),
            restDayCheckService = restDayCheckService,
        )
        val seoul = ZoneId.of("Asia/Seoul")
        // 2026-10-04 15:30 UTC = 서울 10-05 00:30 → 서울 기준 오늘은 10-05, 어제는 10-04
        val now = Instant.parse("2026-10-04T15:30:00Z")
        val seoulYesterday = LocalDate.parse("2026-10-04")

        beforeEach { clearAllMocks() }

        describe("findStreaksAtRisk") {
            it("timezone 기준 어제가 마지막 기록일인 streak을 휴식일 여부와 함께 반환한다") {
                every {
                    streakStateRepository.findAllAtRisk(userIds = listOf(1L, 2L), lastRecordedDate = seoulYesterday)
                } returns listOf(
                    StreakState(userId = 1L, currentStreak = 7, lastRecordedDate = seoulYesterday),
                    StreakState(userId = 2L, currentStreak = 3, lastRecordedDate = seoulYesterday),
                )
                every { restDayCheckService.isRestDay(now, seoul, 1L, 0) } returns false
                every { restDayCheckService.isRestDay(now, seoul, 2L, 0) } returns true

                val result = streakService.findStreaksAtRisk(userIds = listOf(1L, 2L), now = now, timezone = seoul)

                result shouldContainExactly listOf(
                    StreakAtRiskDto(userId = 1L, currentStreak = 7, isRestDay = false),
                    StreakAtRiskDto(userId = 2L, currentStreak = 3, isRestDay = true),
                )
            }

            it("대상 유저가 없으면 조회하지 않는다") {
                val result = streakService.findStreaksAtRisk(userIds = emptyList(), now = now, timezone = seoul)

                result.shouldBeEmpty()
                verify(exactly = 0) { streakStateRepository.findAllAtRisk(any(), any()) }
            }
        }
    })
