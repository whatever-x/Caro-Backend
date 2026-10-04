package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.study.StreakApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

/**
 * 오늘 아직 학습하지 않아 자정에 끊길 streak이 있는 유저에게 현지 시각 [REMINDER_HOUR]시에 푸시를 보낸다.
 *
 * 매시 정각에 실행되며, 그 시점에 현지 시각이 [REMINDER_HOUR]시인 타임존의 유저만 대상으로 한다.
 * (UTC+5:30처럼 30분 단위 타임존도 시(hour)만 비교하므로 하루 한 번 대상이 된다.)
 * 한 유저가 여러 타임존의 기기를 가진 경우 가장 최근에 사용한(lastSeenAt) 기기의 타임존을 따른다.
 */
@Component
class StreakReminderScheduler(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val streakApi: StreakApi,
    private val notificationService: NotificationService,
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
            .filter { isReminderHour(now = now, timezone = it) }
            .toSet()
        if (targetZones.isEmpty()) {
            return 0
        }

        val candidateUserIds = deviceTokenRepository.findAllByTimezoneIn(timezones = targetZones)
            .map { it.userId }
            .distinct()

        // 유저별 대표 타임존 = 가장 최근에 사용한 기기의 타임존
        val usersByZone = deviceTokenRepository.findAllByUserIdIn(userIds = candidateUserIds)
            .groupBy { it.userId }
            .mapValues { (_, tokens) -> tokens.maxBy { it.lastSeenAt }.timezone }
            .filterValues { it in targetZones }
            .entries
            .groupBy(keySelector = { it.value }, valueTransform = { it.key })

        var sentCount = 0
        usersByZone.forEach { (zone, userIds) ->
            val today = now.atZone(ZoneId.of(zone)).toLocalDate()
            userIds.chunked(CHUNK_SIZE).forEach { chunk ->
                streakApi.findStreaksAtRisk(userIds = chunk, today = today).forEach { (userId, currentStreak) ->
                    if (sendReminder(userId = userId, currentStreak = currentStreak)) {
                        sentCount++
                    }
                }
            }
        }
        return sentCount
    }

    private fun sendReminder(
        userId: Long,
        currentStreak: Int,
    ): Boolean =
        try {
            notificationService.sendToUser(userId = userId, message = reminderMessage(currentStreak))
            true
        } catch (e: Exception) {
            // 한 유저의 발송 실패가 나머지 유저 발송을 막지 않도록 한다
            logger.warn(e) { "Streak reminder failed. userId=$userId" }
            false
        }

    private fun isReminderHour(
        now: Instant,
        timezone: String,
    ): Boolean =
        try {
            now.atZone(ZoneId.of(timezone)).hour == REMINDER_HOUR
        } catch (e: DateTimeException) {
            logger.warn { "Invalid timezone in device_tokens. timezone=$timezone" }
            false
        }

    companion object {
        /** 현지 시각 기준 발송 시(hour) */
        const val REMINDER_HOUR = 20

        private const val CHUNK_SIZE = 1000

        fun reminderMessage(
            currentStreak: Int,
        ): PushMessage =
            PushMessage(
                title = "🔥 ${currentStreak}일 연속 학습 중!",
                body = "오늘 학습하고 스트릭을 이어가세요. 자정이 지나면 끊겨요.",
                data = mapOf(
                    "type" to "STREAK_REMINDER",
                    "currentStreak" to currentStreak.toString(),
                ),
            )
    }
}
