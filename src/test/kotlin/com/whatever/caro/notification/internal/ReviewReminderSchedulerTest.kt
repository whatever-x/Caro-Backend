package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushResult
import com.whatever.caro.study.StudyApi
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

class ReviewReminderSchedulerTest :
    DescribeSpec({
        // 2026-10-05 11:00 UTC = 서울 20:00, 뉴욕 07:00
        val now = Instant.parse("2026-10-05T11:00:00Z")
        val seoul = ZoneId.of("Asia/Seoul")
        val newYork = ZoneId.of("America/New_York")

        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val reviewReminderSettingRepository = mockk<ReviewReminderSettingRepository>()
        val studyApi = mockk<StudyApi>()
        val notificationService = mockk<NotificationService>()
        // 실제 messages*.properties를 읽어 다국어 문구까지 검증한다
        val messageSource = ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
        val scheduler = ReviewReminderScheduler(
            deviceTokenRepository = deviceTokenRepository,
            reviewReminderSettingRepository = reviewReminderSettingRepository,
            studyApi = studyApi,
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

        fun setting(
            userId: Long,
            reminderHour: Int,
            enabled: Boolean = true,
        ) = ReviewReminderSetting(userId = userId, enabled = enabled, reminderHour = reminderHour)

        beforeEach {
            clearAllMocks()
            every { notificationService.sendToUser(any(), any()) } returns PushResult.EMPTY
        }

        describe("remind") {
            it("설정 시각이 현지 시각인 유저 중 복습할 카드가 있는 유저에게만 보낸다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul, newYork)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 20), setting(2L, 20))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns
                    listOf(token(1L), token(2L))
                // 2번은 오늘 복습할 카드가 없음
                every { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) } returns setOf(1L)

                val sentCount = scheduler.remind(now = now)

                sentCount shouldBe 1
                verify(exactly = 1) {
                    notificationService.sendToUser(
                        userId = 1L,
                        message = match { it.data["type"] == "REVIEW_REMINDER" },
                    )
                }
                verify(exactly = 0) { notificationService.sendToUser(userId = 2L, message = any()) }
            }

            it("복습할 카드가 없으면 발송하지 않는다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 20))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L)) } returns listOf(token(1L))
                every { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) } returns emptySet()

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { notificationService.sendToUser(any(), any()) }
            }

            it("활성 설정이 없으면 아무것도 조회하지 않는다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    emptyList()

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { deviceTokenRepository.findAllByUserIdIn(any()) }
                verify(exactly = 0) { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) }
            }

            it("대표 기기의 현지 시각이 설정 시각과 다르면 제외한다") {
                // 설정은 7시지만 대표 기기가 서울(지금 20시)이라 대상 아님
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul, newYork)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 7))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L)) } returns listOf(token(1L, timezone = seoul))

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) }
            }

            it("기기가 여러 개면 가장 최근에 사용한 기기의 타임존을 따른다") {
                // 설정 20시. 서울 기기는 예전, 뉴욕 기기를 최근 사용 → 뉴욕 07시라 대상 아님
                val oldSeoul = token(1L, timezone = seoul, lastSeenAt = now.minusSeconds(86_400))
                val recentNewYork = token(1L, timezone = newYork, lastSeenAt = now.minusSeconds(60))
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul, newYork)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 20))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L)) } returns listOf(oldSeoul, recentNewYork)

                scheduler.remind(now = now) shouldBe 0

                verify(exactly = 0) { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) }
            }

            it("한 유저 발송이 실패해도 나머지 유저에게는 보낸다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 20), setting(2L, 20))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns
                    listOf(token(1L), token(2L))
                every { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) } returns setOf(1L, 2L)
                every { notificationService.sendToUser(userId = 1L, message = any()) } throws RuntimeException("boom")

                scheduler.remind(now = now) shouldBe 1

                verify(exactly = 1) { notificationService.sendToUser(userId = 2L, message = any()) }
            }

            it("대표 기기의 언어로 문구를 보낸다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns listOf(seoul)
                every { reviewReminderSettingRepository.findAllByEnabledTrueAndReminderHourIn(any()) } returns
                    listOf(setting(1L, 20), setting(2L, 20))
                every { deviceTokenRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns
                    listOf(token(1L, locale = Locale.KOREAN), token(2L, locale = Locale.ENGLISH))
                every { studyApi.findUserIdsWithStudyToday(any(), any(), any(), any()) } returns setOf(1L, 2L)

                scheduler.remind(now = now) shouldBe 2

                verify(exactly = 1) {
                    notificationService.sendToUser(userId = 1L, message = match { it.title == "복습할 시간이에요 📚" })
                }
                verify(exactly = 1) {
                    notificationService.sendToUser(userId = 2L, message = match { it.title == "Time to review 📚" })
                }
            }
        }

        describe("sendReviewReminders") {
            it("주입된 Clock의 현재 시각으로 실행한다") {
                every { deviceTokenRepository.findDistinctTimezones() } returns emptyList()

                scheduler.sendReviewReminders()

                verify(exactly = 1) { deviceTokenRepository.findDistinctTimezones() }
            }
        }

        describe("reminderMessage") {
            it("한국어면 한국어 문구를 보낸다") {
                val message = scheduler.reminderMessage(Locale.KOREAN)

                message.title shouldBe "복습할 시간이에요 📚"
                message.body shouldBe "오늘 복습할 카드가 있어요. 지금 학습하고 기억을 굳혀요."
                message.data["type"] shouldBe "REVIEW_REMINDER"
            }

            it("한국어가 아니면 영어(기본) 문구를 보낸다") {
                val message = scheduler.reminderMessage(Locale.JAPANESE)

                message.title shouldBe "Time to review 📚"
                message.body shouldBe "You have cards to review today. Study now to lock in what you learned."
            }
        }
    })
