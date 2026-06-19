package com.whatever.caro.study.internal.streak

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface StudyDayRepository : JpaRepository<StudyDay, Long> {
    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        nativeQuery = true,
        value = """
        insert into study_days (user_id, streak_type, study_date, created_at, updated_at)
        values (:userId, 'DAILY_STUDY', :studyDate, NOW(6), NOW(6))
        on duplicate key update
            streak_type = 'DAILY_STUDY',
            updated_at = NOW(6)
        """,
    )
    fun upsertStudied(
        userId: Long,
        studyDate: LocalDate,
    ): Int

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        nativeQuery = true,
        value = """
        insert into study_days (user_id, study_date, streak_type, created_at, updated_at)
        values (:userId, :restDate, 'REST_DAY', NOW(6), NOW(6))
        on duplicate key update
            updated_at = NOW(6)
      """,
    )
    fun insertRestIfAbsent(
        userId: Long,
        restDate: LocalDate,
    ): Int

    fun findAllByUserIdOrderByStudyDateDesc(
        userId: Long,
    ): List<StudyDay>
}
