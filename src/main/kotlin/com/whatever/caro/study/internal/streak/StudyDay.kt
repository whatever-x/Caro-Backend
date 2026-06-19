package com.whatever.caro.study.internal.streak

import com.whatever.caro.common.entity.BaseTimeEntity
import com.whatever.caro.study.StreakType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "study_days")
class StudyDay(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "streak_type", nullable = false)
    val streakType: StreakType,

    @Column(name = "study_date", nullable = false)
    val studyDate: LocalDate,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
