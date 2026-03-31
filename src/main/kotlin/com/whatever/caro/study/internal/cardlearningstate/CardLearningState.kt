package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.common.entity.BaseTimeEntity
import com.whatever.caro.study.CardLearningStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "card_learning_states")
class CardLearningState(
    @Column(name = "card_id", nullable = false, unique = true)
    val cardId: Long,

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

    @Column(name = "new_again_count", nullable = false)
    var newAgainCount: Int = 0,

    @Column(name = "next_review_at")
    var nextReviewAt: Instant? = null,

    @Column(name = "last_reviewed_at")
    var lastReviewedAt: Instant? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
