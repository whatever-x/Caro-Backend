package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.study.StreakApi
import com.whatever.caro.study.StreakAtRiskDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * 오늘 아직 학습하지 않아 자정에 끊길 streak이 있는 유저에게 현지 시각 [REMINDER_HOUR]시에 푸시를 보낸다.
 *
 * 매시 정각에 실행되며, 그 시점에 현지 시각이 [REMINDER_HOUR]시인 타임존의 유저만 대상으로 한다.
 * (UTC+5:30처럼 30분 단위 타임존도 시(hour)만 비교하므로 하루 한 번 대상이 된다.)
 * 한 유저가 여러 기기를 가진 경우 가장 최근에 사용한(lastSeenAt) 기기의 타임존/언어를 따른다.
 * 휴식일(오늘 학습할 카드 없음)인 유저에게는 앱 방문을 유도하는 별도 문구를 보낸다.
 */
@Component
class StreakReminderScheduler(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val streakApi: StreakApi,
    private val notificationService: NotificationService,
    private val messageSource: MessageSource,
    private val clock: Clock,
) {

    @Scheduled(cron = $$"${app.schedule.cron.streak-reminder:-}")
    fun sendStreakReminders() {
        val sentCount = remind(now = Instant.now(clock))
        logger.info { "Streak reminders sent. count=$sentCount" }
    }

    /**
     * @return 리마인더를 보낸 유저 수
     */
    fun remind(
        now: Instant,
    ): Int {
        val targetZones = deviceTokenRepository.findDistinctTimezones()
            .filter { now.atZone(it).hour == REMINDER_HOUR }
            .toSet()
        if (targetZones.isEmpty()) {
            return 0
        }

        val candidateUserIds = deviceTokenRepository.findAllByTimezoneIn(timezones = targetZones)
            .map { it.userId }
            .distinct()

        // 유저별 대표 기기 = 가장 최근에 사용한 기기
        val devicesByZone = deviceTokenRepository.findAllByUserIdIn(userIds = candidateUserIds)
            .groupBy { it.userId }
            .map { (_, tokens) -> tokens.maxBy { it.lastSeenAt } }
            .filter { it.timezone in targetZones }
            .groupBy { it.timezone }

        var sentCount = 0
        devicesByZone.forEach { (zone, devices) ->
            val localeByUserId = devices.associate { it.userId to it.locale }
            devices.map { it.userId }.chunked(CHUNK_SIZE).forEach { chunk ->
                streakApi.findStreaksAtRisk(userIds = chunk, now = now, timezone = zone).forEach { streak ->
                    if (sendReminder(streak = streak, locale = localeByUserId.getValue(streak.userId))) {
                        sentCount++
                    }
                }
            }
        }
        return sentCount
    }

    fun reminderMessage(
        streak: StreakAtRiskDto,
        locale: Locale,
    ): PushMessage {
        val bodyKey = if (streak.isRestDay) BODY_REST_DAY_KEY else BODY_KEY
        return PushMessage(
            title = messageSource.getMessage(TITLE_KEY, arrayOf(streak.currentStreak), locale),
            body = messageSource.getMessage(bodyKey, null, locale),
            data = mapOf(
                "type" to "STREAK_REMINDER",
                "currentStreak" to streak.currentStreak.toString(),
                "restDay" to streak.isRestDay.toString(),
            ),
        )
    }

    private fun sendReminder(
        streak: StreakAtRiskDto,
        locale: Locale,
    ): Boolean =
        try {
            notificationService.sendToUser(userId = streak.userId, message = reminderMessage(streak, locale))
            true
        } catch (e: Exception) {
            // 한 유저의 발송 실패가 나머지 유저 발송을 막지 않도록 한다
            logger.warn(e) { "Streak reminder failed. userId=${streak.userId}" }
            false
        }

    companion object {
        /** 현지 시각 기준 발송 시(hour) */
        const val REMINDER_HOUR = 20

        private const val CHUNK_SIZE = 1000
        private const val TITLE_KEY = "notification.streak_reminder.title"
        private const val BODY_KEY = "notification.streak_reminder.body"
        private const val BODY_REST_DAY_KEY = "notification.streak_reminder.rest_day.body"
    }
}
