package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushResult
import com.whatever.caro.study.StreakApi
import com.whatever.caro.study.StreakAtRiskDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.support.ResourceBundleMessageSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class StreakReminderSchedulerTest :
    DescribeSpec({
        // 2026-10-05 11:00 UTC = 서울 20:00, 뉴욕 07:00
        val now = Instant.parse("2026-10-05T11:00:00Z")
        val seoul = ZoneId.of("Asia/Seoul")
        val newYork = ZoneId.of("America/New_York")

        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val streakApi = mockk<StreakApi>()
        val notificationService = mockk<NotificationService>()
        // 실제 messages*.properties를 읽어 다국어 문구까지 검증한다
        val messageSource = ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
        val scheduler = StreakReminderScheduler(
            deviceTokenRepository = deviceTokenRepository,
            streakApi = streakApi,
            notificationService = notificationService,
            messageSource = messageSource,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        fun token(
            userId: Long,
            timezone: ZoneId = seoul,
            locale: Locale = Locale.KOREAN,
            lastSeenAt: Instant = now.minusSeconds(60),
        ) = DeviceToken(
            userId = userId,
            token = "token-$userId-$timezone",
            platform = DevicePlatform.ANDROID,
            timezone = timezone,
            locale = locale,
            lastSeenAt = lastSeenAt,
        )

        fun atRisk(
            userId: Long,
            currentStreak: Int,
            isRestDay: Boolean = false,
        ) = StreakAtRiskDto(userId = userId, currentStreak = currentStreak, isRestDay = isRestDay)

        beforeEach {
            clearAllMocks()
            every { notificationService.sendToUser(any(), any()) } returns PushResult.EMPTY
        }

        describe("remind") {
            it("현지 시각이 20시인 타임존의 유저 중 streak이 끊기기 직전인 유저에게만 보낸다") {
                val seoulTokens = listOf(token(1L), token(2L))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul, newYork)
                every { deviceTokenRepository.findAllByTimezoneIn(setOf(seoul)) } returns seoulTokens
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns seoulTokens
                // 1번만 위험(어제까지 7일), 2번은 오늘 이미 학습함
                every { streakApi.findStreaksAtRisk(listOf(1L, 2L), now, seoul) } returns listOf(atRisk(1L, 7))

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
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(newYork)

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { deviceTokenRepository.findAllByTimezoneIn(any()) }
                verify(exactly = 0) { streakApi.findStreaksAtRisk(any(), any(), any(), any()) }
            }

            it("기기가 여러 개면 가장 최근에 사용한 기기의 타임존을 따른다") {
                // 서울 기기는 예전, 뉴욕 기기를 최근에 사용 → 지금(서울 20시)은 대상이 아니다
                val oldSeoul = token(1L, timezone = seoul, lastSeenAt = now.minusSeconds(86_400))
                val recentNewYork = token(1L, timezone = newYork, lastSeenAt = now.minusSeconds(60))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul, newYork)
                every { deviceTokenRepository.findAllByTimezoneIn(setOf(seoul)) } returns listOf(oldSeoul)
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L)) } returns listOf(oldSeoul, recentNewYork)

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { streakApi.findStreaksAtRisk(any(), any(), any(), any()) }
            }

            it("대표 기기의 언어로 문구를 보낸다") {
                val tokens = listOf(token(1L, locale = Locale.KOREAN), token(2L, locale = Locale.ENGLISH))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { deviceTokenRepository.findAllByTimezoneIn(setOf(seoul)) } returns tokens
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns tokens
                every { streakApi.findStreaksAtRisk(listOf(1L, 2L), now, seoul) } returns listOf(
                    atRisk(1L, 3),
                    atRisk(2L, 5),
                )

                scheduler.remind(now = now) shouldBe 2

                verify(exactly = 1) {
                    notificationService.sendToUser(userId = 1L, message = match { it.title == "🔥 3일 연속 학습 중!" })
                }
                verify(exactly = 1) {
                    notificationService.sendToUser(userId = 2L, message = match { it.title == "🔥 5-day streak!" })
                }
            }

            it("한 유저 발송이 실패해도 나머지 유저에게는 보낸다") {
                val tokens = listOf(token(1L), token(2L))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { deviceTokenRepository.findAllByTimezoneIn(setOf(seoul)) } returns tokens
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns tokens
                every { streakApi.findStreaksAtRisk(listOf(1L, 2L), now, seoul) } returns listOf(
                    atRisk(1L, 3),
                    atRisk(2L, 5),
                )
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
            it("학습일이면 학습을 유도하는 문구를 보낸다") {
                val message = scheduler.reminderMessage(atRisk(1L, 12), Locale.KOREAN)

                message.title shouldBe "🔥 12일 연속 학습 중!"
                message.body shouldBe "오늘 학습하고 스트릭을 이어가세요. 자정이 지나면 끊겨요."
                message.data["currentStreak"] shouldBe "12"
                message.data["restDay"] shouldBe "false"
            }

            it("휴식일이면 앱 방문을 유도하는 별도 문구를 보낸다") {
                val message = scheduler.reminderMessage(atRisk(1L, 12, isRestDay = true), Locale.KOREAN)

                message.body shouldBe "오늘은 복습할 카드가 없는 휴식일이에요. 앱에 들어와서 스트릭을 이어가세요."
                message.data["restDay"] shouldBe "true"
            }

            it("한국어가 아니면 영어(기본) 문구를 보낸다") {
                val message = scheduler.reminderMessage(atRisk(1L, 12, isRestDay = true), Locale.JAPANESE)

                message.title shouldBe "🔥 12-day streak!"
                message.body shouldBe "No cards due today. Open the app to keep your streak going."
            }
        }
    })
