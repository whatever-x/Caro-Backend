package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.study.StudyApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * 유저가 설정한 현지 시각에, 그날 복습(출제)할 카드가 있는 유저에게만 푸시를 보낸다.
 *
 * 매시 정각에 실행되며, 그 시점에 현지 시각이 유저의 설정 시(hour)와 같은 유저만 대상으로 한다.
 * 기준 시간대는 유저의 대표 기기(가장 최근 사용) 타임존이며 언어도 그 기기를 따른다.
 * 복습할 카드가 없는 유저는 발송하지 않는다(완료 조건: 카드 없으면 생략).
 */
@Component
class ReviewReminderScheduler(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val reviewReminderSettingRepository: ReviewReminderSettingRepository,
    private val studyApi: StudyApi,
    private val notificationService: NotificationService,
    private val messageSource: MessageSource,
    private val clock: Clock,
) {

    @Scheduled(cron = $$"${app.schedule.cron.review-reminder:-}")
    fun sendReviewReminders() {
        val sentCount = remind(now = Instant.now(clock))
        logger.info { "Review reminders sent. count=$sentCount" }
    }

    /**
     * @return 리마인더를 보낸 유저 수
     */
    fun remind(
        now: Instant,
    ): Int {
        val zones = deviceTokenRepository.findDistinctTimezones()
        if (zones.isEmpty()) {
            return 0
        }
        val localHourByZone = zones.associateWith { now.atZone(it).hour }

        // 지금 어느 타임존에선가 현지 시각인 시(hour)로 활성 설정을 먼저 좁힌다.
        val settings = reviewReminderSettingRepository
            .findAllByEnabledTrueAndReminderHourIn(reminderHours = localHourByZone.values.toSet())
        if (settings.isEmpty()) {
            return 0
        }
        val reminderHourByUser = settings.associate { it.userId to it.reminderHour }

        // 유저별 대표 기기 = 가장 최근에 사용한 기기. 그 기기 타임존의 현지 시각이 설정 시각과 같을 때만 대상.
        val representativeByUser = deviceTokenRepository.findAllByUserIdIn(userIds = settings.map { it.userId })
            .groupBy { it.userId }
            .mapValues { (_, tokens) -> tokens.maxBy { it.lastSeenAt } }

        val targetDevices = reminderHourByUser.keys.mapNotNull { userId ->
            val device = representativeByUser[userId] ?: return@mapNotNull null
            if (localHourByZone[device.timezone] == reminderHourByUser[userId]) device else null
        }
        if (targetDevices.isEmpty()) {
            return 0
        }

        var sentCount = 0
        // 같은 타임존끼리 묶어 "오늘 복습할 카드 있음"을 한 번에 질의한다(같은 오늘 기준).
        targetDevices.groupBy { it.timezone }.forEach { (zone, devices) ->
            val localeByUserId = devices.associate { it.userId to it.locale }
            val userIds = devices.map { it.userId }
            val userIdsWithStudy = studyApi.findUserIdsWithStudyToday(userIds = userIds, now = now, timezone = zone)
            userIds.filter { it in userIdsWithStudy }.forEach { userId ->
                if (send(userId = userId, locale = localeByUserId.getValue(userId))) {
                    sentCount++
                }
            }
        }
        return sentCount
    }

    fun reminderMessage(
        locale: Locale,
    ): PushMessage =
        PushMessage(
            title = messageSource.getMessage(TITLE_KEY, null, locale),
            body = messageSource.getMessage(BODY_KEY, null, locale),
            data = mapOf("type" to REVIEW_REMINDER_TYPE),
        )

    private fun send(
        userId: Long,
        locale: Locale,
    ): Boolean =
        try {
            notificationService.sendToUser(userId = userId, message = reminderMessage(locale))
            true
        } catch (e: Exception) {
            // 한 유저의 발송 실패가 나머지 유저 발송을 막지 않도록 한다
            logger.warn(e) { "Review reminder failed. userId=$userId" }
            false
        }

    companion object {
        private const val TITLE_KEY = "notification.review_reminder.title"
        private const val BODY_KEY = "notification.review_reminder.body"

        /** 앱이 알림 탭 시 오늘의 학습으로 라우팅하는 식별자 */
        private const val REVIEW_REMINDER_TYPE = "REVIEW_REMINDER"
    }
}
