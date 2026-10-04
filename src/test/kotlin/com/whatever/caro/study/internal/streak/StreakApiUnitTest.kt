package com.whatever.caro.study.internal.streak

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class StreakApiUnitTest :
    DescribeSpec({
        val streakStateRepository = mockk<StreakStateRepository>()
        val streakService = StreakService(
            streakStateRepository = streakStateRepository,
            studyDayRepository = mockk(),
            restDayCheckService = mockk(),
        )
        val today = LocalDate.parse("2026-10-05")

        beforeEach { clearAllMocks() }

        describe("findStreaksAtRisk") {
            it("마지막 기록일이 어제인 streak을 userId → 현재 streak으로 반환한다") {
                every {
                    streakStateRepository.findAllAtRisk(userIds = listOf(1L, 2L), lastRecordedDate = today.minusDays(1))
                } returns listOf(
                    StreakState(userId = 1L, currentStreak = 7, lastRecordedDate = today.minusDays(1)),
                )

                val result = streakService.findStreaksAtRisk(userIds = listOf(1L, 2L), today = today)

                result shouldBe mapOf(1L to 7)
            }

            it("대상 유저가 없으면 조회하지 않는다") {
                val result = streakService.findStreaksAtRisk(userIds = emptyList(), today = today)

                result.shouldBeEmpty()
                verify(exactly = 0) { streakStateRepository.findAllAtRisk(any(), any()) }
            }
        }
    })
