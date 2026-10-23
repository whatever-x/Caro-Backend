package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.common.jdbc.MultiRowInsert
import com.whatever.caro.common.jdbc.setUtcTimestamp
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Types
import java.time.Instant

@Transactional(propagation = Propagation.MANDATORY)
class CardLearningStateBulkRepositoryImpl(
    jdbcTemplate: JdbcTemplate,
) : CardLearningStateBulkRepository {
    // DB DEFAULT에 기대지 않고 엔티티 생성자 기본값을 모든 컬럼에 바인딩해야 JPA로 저장한 행과 같아진다.
    private val multiRowInsert = MultiRowInsert(
        jdbcTemplate = jdbcTemplate,
        table = "card_learning_states",
        columns = listOf(
            "card_id",
            "user_id",
            "deck_id",
            "status",
            "previous_status",
            "interval_days",
            "repetitions",
            "ease_factor",
            "lapses",
            "consecutive_again_count",
            "total_reviews",
            "last_reviewed_date",
            "next_review_date",
            "created_at",
            "updated_at",
            "deleted_at",
        ),
        rowValues = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
    )

    override fun insertAll(
        states: List<CardLearningState>,
        now: Instant,
    ) {
        multiRowInsert.insert(
            rows = states,
            bindRow = { statement, state, utc ->
                statement.setLong(1, state.cardId)
                statement.setLong(2, state.userId)
                statement.setLong(3, state.deckId)
                statement.setString(4, state.status.name)
                statement.setString(5, state.previousStatus?.name)
                statement.setInt(6, state.intervalDays)
                statement.setInt(7, state.repetitions)
                statement.setBigDecimal(8, state.easeFactor)
                statement.setInt(9, state.lapses)
                statement.setInt(10, state.consecutiveAgainCount)
                statement.setInt(11, state.totalReviews)
                statement.setObject(12, state.lastReviewedDate, Types.DATE)
                statement.setObject(13, state.nextReviewDate, Types.DATE)
                statement.setUtcTimestamp(14, now, utc)
                statement.setUtcTimestamp(15, now, utc)
            },
        )
    }
}
