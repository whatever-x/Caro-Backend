package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushResult
import com.whatever.caro.study.StreakApi
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StreakReminderSchedulerTest :
    DescribeSpec({
        // 2026-10-05 11:00 UTC = 서울 20:00, 뉴욕 07:00
        val now = Instant.parse("2026-10-05T11:00:00Z")
        val seoulToday = LocalDate.parse("2026-10-05")

        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val streakApi = mockk<StreakApi>()
        val notificationService = mockk<NotificationService>()
        val scheduler = StreakReminderScheduler(
            deviceTokenRepository = deviceTokenRepository,
            streakApi = streakApi,
            notificationService = notificationService,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        fun token(
            userId: Long,
            timezone: String,
            lastSeenAt: Instant = now.minusSeconds(60),
        ) = DeviceToken(
            userId = userId,
            token = "token-$userId-$timezone",
            platform = DevicePlatform.ANDROID,
            timezone = timezone,
            lastSeenAt = lastSeenAt,
        )

        beforeEach {
            clearAllMocks()
            every { notificationService.sendToUser(any(), any()) } returns PushResult.EMPTY
        }

        describe("remind") {
            it("현지 시각이 20시인 타임존의 유저 중 streak이 끊기기 직전인 유저에게만 보낸다") {
                val seoulTokens = listOf(token(1L, "Asia/Seoul"), token(2L, "Asia/Seoul"))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf("Asia/Seoul", "America/New_York")
                every { deviceTokenRepository.findAllByTimezoneIn(setOf("Asia/Seoul")) } returns seoulTokens
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns seoulTokens
                // 1번만 위험(어제까지 7일), 2번은 오늘 이미 학습함
                every { streakApi.findStreaksAtRisk(listOf(1L, 2L), seoulToday) } returns mapOf(1L to 7)

                val sentCount = scheduler.remind(now = now)

                sentCount shouldBe 1
                verify(exactly = 1) {
                    notificationService.sendToUser(
                        userId = 1L,
                        message = match { it.data["type"] == "STREAK_REMINDER" && it.data["currentStreak"] == "7" },
                    )
                }
                verify(exactly = 0) { notificationService.sendToUser(userId = 2L, message = any()) }
            }

            it("20시인 타임존이 없으면 아무것도 조회하지 않는다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf("America/New_York")

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { deviceTokenRepository.findAllByTimezoneIn(any()) }
                verify(exactly = 0) { streakApi.findStreaksAtRisk(any(), any()) }
            }

            it("잘못된 타임존 값은 무시한다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf("Not/AZone")

                scheduler.remind(now = now) shouldBe 0
            }

            it("기기가 여러 타임존이면 가장 최근에 사용한 기기의 타임존을 따른다") {
                // 서울 기기는 예전, 뉴욕 기기를 최근에 사용 → 지금(서울 20시)은 대상이 아니다
                val oldSeoul = token(1L, "Asia/Seoul", lastSeenAt = now.minusSeconds(86_400))
                val recentNewYork = token(1L, "America/New_York", lastSeenAt = now.minusSeconds(60))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf("Asia/Seoul", "America/New_York")
                every { deviceTokenRepository.findAllByTimezoneIn(setOf("Asia/Seoul")) } returns listOf(oldSeoul)
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L)) } returns listOf(oldSeoul, recentNewYork)

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { streakApi.findStreaksAtRisk(any(), any()) }
            }

            it("한 유저 발송이 실패해도 나머지 유저에게는 보낸다") {
                val tokens = listOf(token(1L, "Asia/Seoul"), token(2L, "Asia/Seoul"))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf("Asia/Seoul")
                every { deviceTokenRepository.findAllByTimezoneIn(setOf("Asia/Seoul")) } returns tokens
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns tokens
                every { streakApi.findStreaksAtRisk(listOf(1L, 2L), seoulToday) } returns mapOf(1L to 3, 2L to 5)
                every { notificationService.sendToUser(userId = 1L, message = any()) } throws RuntimeException("boom")

                scheduler.remind(now = now) shouldBe 1

                verify(exactly = 1) { notificationService.sendToUser(userId = 2L, message = any()) }
            }
        }

        describe("sendStreakReminders") {
            it("주입된 Clock의 현재 시각으로 실행한다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns emptyList()

                scheduler.sendStreakReminders()

                verify(exactly = 1) { deviceTokenRepository.findDistinctTimezones() }
            }
        }

        describe("reminderMessage") {
            it("현재 streak을 제목과 data에 담는다") {
                val message = StreakReminderScheduler.reminderMessage(currentStreak = 12)

                message.title shouldBe "🔥 12일 연속 학습 중!"
                message.data["currentStreak"] shouldBe "12"
            }
        }
    })
