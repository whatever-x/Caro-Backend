package com.whatever.caro.study.internal.streak

import com.whatever.caro.study.StreakType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@Service
class StreakService(
    private val streakStateRepository: StreakStateRepository,
    private val studyDayRepository: StudyDayRepository,
    private val restDayCheckService: RestDayCheckService,
) {
    @Transactional
    fun syncWithRestDayCheck(
        userId: Long,
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ) {
        val today = toStreakDate(now, timezone, dayCutoffHour)
        val alreadyRecorded = studyDayRepository.existsByUserIdAndStudyDate(
            userId = userId,
            studyDate = today,
        )

        val isRestDay = !alreadyRecorded && restDayCheckService.isRestDay(
            userId = userId,
            now = now,
            timezone = timezone,
            dayCutoffHour = dayCutoffHour,
        )

        if (isRestDay) {
            recordRest(
                userId = userId,
                restDate = today,
            )
        }
        sync(
            userId = userId,
            now = now,
            timezone = timezone,
            dayCutoffHour = dayCutoffHour,
        )
    }

    /**
     * "오늘 기준" streak을 반환한다.
     *
     * streak 끊김 여부는 `streak이 마지막으로 기록된 시점`과 `timezone 기준 오늘`로 판단하므로,
     * timezone이 바뀌었을 경우 [sync]로 갱신한 뒤 조회해야 정확하다.
     */
    @Transactional(readOnly = true)
    fun getStreak(
        userId: Long,
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): StreakStatusResult {
        val streakState = streakStateRepository.findByUserId(userId = userId)
            ?: return StreakStatusResult.NotStarted

        val currentDate = toStreakDate(now, timezone, dayCutoffHour)
        if (isStreakAlive(streakState.lastRecordedDate, currentDate)) {
            return StreakStatusResult.Active(currentStreak = streakState.currentStreak)
        }

        return StreakStatusResult.Broken
    }

    /**
     * 앱 진입 등 상호작용 시점에 호출되어, 저장된 current streak을 "오늘 기준" 값으로 동기화한다.
     * streak 끊김은 학습 기록이 없는 것으로 판단하기 때문에,
     * current streak을 0으로 persist하는 것은 이 메서드의 책임이다.
     *
     * timezone이 미래로 이동(예: UTC-12 → UTC+14)해 sync되면 streak은 끊긴다.
     * timezone이 과거로 이동(예: UTC+14 → UTC-12)해 sync되면 로그에서 streak을 재계산한다.
     */
    @Transactional
    fun sync(
        userId: Long,
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ) {
        val streakState = streakStateRepository.findByUserIdForUpdate(userId = userId)
            ?: return
        val daysDesc = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId)
        if (daysDesc.isEmpty()) {
            return
        }

        val lastRecordedDate = daysDesc.first().studyDate
        val currentDate = toStreakDate(now, timezone, dayCutoffHour)

        val currentStreak = if (isStreakAlive(streakState.lastRecordedDate, currentDate)) {
            computeStreak(daysDesc = daysDesc)
        } else {
            resolveGap(
                userId = userId,
                previousDate = streakState.lastRecordedDate,
                currentDate = currentDate,
            )
            0
        }

        streakState.updateCurrentStreak(
            currentStreak = currentStreak,
            lastRecordedDate = lastRecordedDate,
        )
        streakStateRepository.save(streakState)
    }

    @Transactional
    fun recordStudied(
        userId: Long,
        studyDate: LocalDate,
    ) {
        val effectedRows = studyDayRepository.upsertStudied(
            userId = userId,
            studyDate = studyDate,
        )
        when (effectedRows) {
            0 -> return
            else -> reconcile(userId = userId, recordedDate = studyDate)
        }
    }

    @Transactional
    fun recordRest(
        userId: Long,
        restDate: LocalDate,
    ) {
        val effectedRows = studyDayRepository.insertRestIfAbsent(
            userId = userId,
            restDate = restDate,
        )
        when (effectedRows) {
            0, 2 -> return
            else -> reconcile(userId = userId, recordedDate = restDate)
        }
    }

    private fun reconcile(
        userId: Long,
        recordedDate: LocalDate,
    ) {
        val streakState = streakStateRepository.findByUserIdForUpdate(userId = userId)
            ?: StreakState(
                userId = userId,
                lastRecordedDate = recordedDate,
            )

        if (isStreakAlive(streakState.lastRecordedDate, recordedDate).not()) {
            resolveGap(
                userId = userId,
                previousDate = streakState.lastRecordedDate,
                currentDate = recordedDate,
            )
        }

        val daysDesc = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId)
        streakState.updateCurrentStreak(
            currentStreak = computeStreak(daysDesc),
            lastRecordedDate = daysDesc.first().studyDate, // 항상 최신 study date를 기록
        )
        streakStateRepository.save(streakState)
    }

    private fun computeStreak(
        daysDesc: List<StudyDay>,
    ): Int {
        if (daysDesc.isEmpty()) {
            return 0
        }

        var streakCount = 0
        var expectedDate = daysDesc.first().studyDate
        for (day in daysDesc) {
            if (day.studyDate != expectedDate) {
                break
            }
            if (day.streakType == StreakType.DAILY_STUDY) {
                streakCount++
            }
            expectedDate = expectedDate.minusDays(1)
        }

        return streakCount
    }

    private fun resolveGap(
        userId: Long,
        previousDate: LocalDate,
        currentDate: LocalDate,
    ) {
        if (isStreakAlive(previousDate, currentDate)) {
            return
        }

        // 현재는 Timezone 이동으로 인한 시간 끊김 보정하지 않음
        logger.info {
            "Streak gap detected. userId=$userId, previousDate=$previousDate, currentDate=$currentDate"
        }
    }

    private fun isStreakAlive(
        lastRecordedDate: LocalDate,
        currentDate: LocalDate,
    ): Boolean = ChronoUnit.DAYS.between(lastRecordedDate, currentDate) <= 1

    private fun toStreakDate(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): LocalDate = now.atZone(timezone).minusHours(dayCutoffHour.toLong()).toLocalDate()
}
