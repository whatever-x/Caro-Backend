package com.whatever.caro.study.internal.studysession

import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "review_logs")
@EntityListeners(AuditingEntityListener::class)
class ReviewLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_session_id", nullable = false)
    val studySession: StudySession,

    @Column(name = "card_id", nullable = false)
    val cardId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val rating: Rating,

    @Column(name = "time_ms", nullable = false)
    val timeMs: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false)
    val reviewType: ReviewType,

    @Column(name = "previous_interval_days", nullable = false)
    val previousIntervalDays: Int,

    @Column(name = "previous_ease_factor", nullable = false, precision = 3, scale = 2)
    val previousEaseFactor: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_card_status", nullable = false)
    val previousCardStatus: CardLearningStatus,

    @Column(name = "interval_days", nullable = false)
    val intervalDays: Int,

    @Column(name = "ease_factor", nullable = false, precision = 3, scale = 2)
    val easeFactor: BigDecimal,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // Append only용도이므로 별도로 @CreatedDate만 사용
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        private set
}
