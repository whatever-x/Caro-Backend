package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.common.entity.SoftDeletableEntity
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.SchedulingState
import com.whatever.caro.study.internal.SchedulingState.New
import com.whatever.caro.study.internal.SchedulingState.Review
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "card_learning_states")
class CardLearningState(
    @Column(name = "card_id", nullable = false, unique = true)
    val cardId: Long,

    @Column(name = "deck_id", nullable = false)
    val deckId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CardLearningStatus = CardLearningStatus.NEW,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    var previousStatus: CardLearningStatus? = null,

    @Column(name = "interval_days", nullable = false)
    var intervalDays: Int = 0,

    @Column(nullable = false)
    var repetitions: Int = 0,

    @Column(name = "ease_factor", nullable = false, precision = 3, scale = 2)
    var easeFactor: BigDecimal = BigDecimal("2.50"),

    @Column(nullable = false)
    var lapses: Int = 0,

    @Column(name = "next_review_date")
    var nextReviewDate: LocalDate? = null,

    @Column(name = "last_reviewed_date")
    var lastReviewedDate: LocalDate? = null,

    @Column(name = "consecutive_again_count", nullable = false)
    var consecutiveAgainCount: Int = 0,

    @Column(name = "total_reviews", nullable = false)
    var totalReviews: Int = 0,
) : SoftDeletableEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun toSchedulingState(): SchedulingState =
        when (status) {
            CardLearningStatus.NEW -> New(
                easeFactor = easeFactor,
                consecutiveAgainCount = consecutiveAgainCount,
            )

            CardLearningStatus.REVIEW -> Review(
                easeFactor = easeFactor,
                intervalDays = intervalDays,
                repetitions = repetitions,
                lapses = lapses,
                lastReviewedDate = lastReviewedDate,
                nextReviewDate = nextReviewDate,
                consecutiveAgainCount = consecutiveAgainCount,
            )

            CardLearningStatus.SUSPENDED -> error("SUSPENDED card must not enter scheduling")
        }

    fun applyScheduling(
        studyDate: LocalDate,
        nextState: SchedulingState,
    ) {
        this.previousStatus = this.status
        when (nextState) {
            is New -> {
                this.status = CardLearningStatus.NEW
                this.easeFactor = nextState.easeFactor
                this.consecutiveAgainCount = nextState.consecutiveAgainCount
            }

            is Review -> {
                this.status = CardLearningStatus.REVIEW
                this.easeFactor = nextState.easeFactor
                this.intervalDays = nextState.intervalDays
                this.repetitions = nextState.repetitions
                this.lapses = nextState.lapses
                this.nextReviewDate = nextState.nextReviewDate
                this.consecutiveAgainCount = nextState.consecutiveAgainCount
            }
        }

        this.lastReviewedDate = studyDate
        this.totalReviews++
    }
}
