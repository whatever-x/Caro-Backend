package com.whatever.caro.card.internal.card

import com.whatever.caro.common.jdbc.MultiRowInsert
import com.whatever.caro.common.jdbc.setUtcTimestamp
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional(propagation = Propagation.MANDATORY)
class CardBulkRepositoryImpl(
    jdbcTemplate: JdbcTemplate,
) : CardBulkRepository {
    private val multiRowInsert = MultiRowInsert(
        jdbcTemplate = jdbcTemplate,
        table = "cards",
        columns = listOf("card_template_id", "note_id", "deck_id", "user_id", "created_at", "updated_at", "deleted_at"),
        rowValues = "(?, ?, ?, ?, ?, ?, NULL)",
    )

    override fun insertAll(
        rows: List<CardInsertRow>,
        now: Instant,
    ): List<Long> =
        multiRowInsert.insertReturningKeys(
            rows = rows,
            bindRow = { statement, row, utc ->
                statement.setLong(1, row.cardTemplateId)
                statement.setLong(2, row.noteId)
                statement.setLong(3, row.deckId)
                statement.setLong(4, row.userId)
                statement.setUtcTimestamp(5, now, utc)
                statement.setUtcTimestamp(6, now, utc)
            },
        )
}
