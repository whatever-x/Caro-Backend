package com.whatever.caro.study.internal.studysession

import com.whatever.caro.common.entity.BaseTimeEntity
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.Transient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Entity
@Table(name = "study_sessions")
class StudySession(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "deck_id", nullable = false)
    val deckId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StudySessionStatus = StudySessionStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "study_type", nullable = false)
    val studyType: StudyType,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: Instant,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "new_cards_studied", nullable = false)
    var newCardsStudied: Int = 0,

    @Column(name = "review_cards_studied", nullable = false)
    var reviewCardsStudied: Int = 0,

    @Column(name = "new_cards_goal", nullable = false)
    var newCardsGoal: Int = 0,

    @Column(name = "review_cards_goal", nullable = false)
    var reviewCardsGoal: Int = 0,

    @Column(name = "timezone", nullable = false, length = 64, updatable = false)
    val timezone: ZoneId,

    @Column(name = "day_cutoff_hour", nullable = false, updatable = false)
    val dayCutoffHour: Int,

    @Column(name = "deck_preset_id_snapshot", nullable = false, updatable = false)
    val deckPresetIdSnapshot: Long,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "session_date", nullable = false, updatable = false)
    val sessionDate: LocalDate = startedAt.atZone(timezone).minusHours(dayCutoffHour.toLong()).toLocalDate()

    @get:Transient
    val estimatedTotal: Int
        get() = newCardsGoal + reviewCardsGoal

    /**
     * 세션의 시작 경계(논리적인 시작 시간)
     */
    @get:Transient
    val sessionStart: Instant
        get() = sessionDate.atTime(dayCutoffHour, 0).atZone(timezone).toInstant()

    // TODO 제거예정
    @Deprecated("절대 시간이 아닌 상대시간 사용 예정으로, 제거되어야함")
    @get:Transient
    val nextSessionStart: Instant
        get() = sessionDate.plusDays(1).atTime(dayCutoffHour, 0).atZone(timezone).toInstant()

    fun isTodaySession(
        now: Instant,
        clientTimezone: ZoneId,
    ): Boolean = sessionDate == now.atZone(clientTimezone).minusHours(dayCutoffHour.toLong()).toLocalDate()

    fun updateStudiedCard(
        status: ReviewType,
    ) {
        when (status) {
            ReviewType.NEW -> newCardsStudied++
            ReviewType.REVIEW -> reviewCardsStudied++
        }
    }

    private fun complete(
        now: Instant,
    ) {
        status = StudySessionStatus.COMPLETED
        endedAt = now
    }

    fun recalculateGoals(
        availableNewGoal: Int,
        availableReviewGoal: Int,
    ) {
        newCardsGoal = (newCardsStudied + availableNewGoal).coerceAtMost(newCardsGoal)
        reviewCardsGoal = (reviewCardsStudied + availableReviewGoal).coerceAtMost(reviewCardsGoal)
    }

    fun completeIfGoalAchieved(
        now: Instant,
    ) {
        if ((newCardsStudied >= newCardsGoal) && (reviewCardsStudied >= reviewCardsGoal)) {
            complete(now)
        }
        if (newCardsGoal == 0 && reviewCardsGoal == 0) {
            logger.debug { "All cards deleted. Session status updated: $status. sessionId: $id" }
        }
    }
}
