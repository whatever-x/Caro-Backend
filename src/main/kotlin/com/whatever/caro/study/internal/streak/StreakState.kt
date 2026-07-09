package com.whatever.caro.study.internal.streak

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "streak_states")
class StreakState(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "current_streak", nullable = false)
    var currentStreak: Int = 0,

    @Column(name = "last_recorded_date", nullable = false)
    var lastRecordedDate: LocalDate,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun updateCurrentStreak(
        currentStreak: Int,
        lastRecordedDate: LocalDate,
    ) {
        this.currentStreak = currentStreak
        this.lastRecordedDate = lastRecordedDate
    }
}
