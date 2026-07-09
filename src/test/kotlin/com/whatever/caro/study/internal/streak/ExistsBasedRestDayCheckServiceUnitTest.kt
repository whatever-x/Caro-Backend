package com.whatever.caro.study.internal.streak

import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId

class ExistsBasedRestDayCheckServiceUnitTest :
    DescribeSpec({

        val clsRepository = mockk<CardLearningStateRepository>()
        val service = ExistsBasedRestDayCheckService(
            cardLearningStateRepository = clsRepository,
        )

        afterTest {
            clearMocks(clsRepository)
        }

        val userId = 1L
        val kst = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-06-20T03:00:00Z")

        describe("isRestDay") {
            it("오늘 학습할 NEW 카드가 있으면 휴식일이 아니다") {
                every { clsRepository.existsNewCardByUser(userId) } returns true

                val result = service.isRestDay(now = now, timezone = kst, userId = userId, dayCutoffHour = 0)
                result shouldBe false
            }

            it("오늘 복습할 REVIEW 카드가 있으면 휴식일이 아니다") {
                every { clsRepository.existsNewCardByUser(userId) } returns false
                every { clsRepository.existsTodayReviewCardByUser(userId, any()) } returns true

                val result = service.isRestDay(now = now, timezone = kst, userId = userId, dayCutoffHour = 0)
                result shouldBe false
            }

            it("오늘 학습 대상이 없지만, 카드를 보유하고 있으면 휴식일이다") {
                every { clsRepository.existsNewCardByUser(userId) } returns false
                every { clsRepository.existsTodayReviewCardByUser(userId, any()) } returns false
                every { clsRepository.existsByUserIdAndDeletedAtIsNull(userId) } returns true

                val result = service.isRestDay(now = now, timezone = kst, userId = userId, dayCutoffHour = 0)
                result shouldBe true
            }

            it("카드가 전혀 없으면 휴식일이 아니다") {
                every { clsRepository.existsNewCardByUser(userId) } returns false
                every { clsRepository.existsTodayReviewCardByUser(userId, any()) } returns false
                every { clsRepository.existsByUserIdAndDeletedAtIsNull(userId) } returns false

                val result = service.isRestDay(now = now, timezone = kst, userId = userId, dayCutoffHour = 0)
                result shouldBe false
            }
        }
    })
