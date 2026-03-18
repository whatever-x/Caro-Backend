package com.whatever.caro.study.internal.studysession

import com.whatever.caro.common.entity.BaseTimeEntity
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "study_session")
class StudySession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

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
) : BaseTimeEntity()
