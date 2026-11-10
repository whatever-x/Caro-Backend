package com.whatever.caro.card.internal.note

import com.whatever.caro.common.jdbc.MultiRowInsert
import com.whatever.caro.common.jdbc.setUtcTimestamp
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

@Transactional(propagation = Propagation.MANDATORY)
class NoteBulkRepositoryImpl(
    jdbcTemplate: JdbcTemplate,
) : NoteBulkRepository {
    private val multiRowInsert = MultiRowInsert(
        jdbcTemplate = jdbcTemplate,
        table = "notes",
        columns = listOf("user_id", "fields", "created_at", "updated_at", "deleted_at"),
        rowValues = "(?, CAST(? AS JSON), ?, ?, NULL)",
    )

    // Spring 빈을 쓰면 웹 응답의 JSON 설정 변경이 저장되는 JSON 텍스트에도 반영되므로, Hibernate처럼 설정과 무관한 기본 매퍼를 쓴다.
    private val jsonMapper = JsonMapper.builder().build()

    override fun insertAll(
        rows: List<NoteInsertRow>,
        now: Instant,
    ): List<Long> {
        rows.forEach { checkReadLimits(it.fields) }
        val serializedRows = rows.map {
            SerializedNote(userId = it.userId, json = jsonMapper.writeValueAsString(it.fields))
        }
        return multiRowInsert.insertReturningKeys(
            rows = serializedRows,
            bindRow = { statement, row, utc ->
                statement.setLong(1, row.userId)
                statement.setString(2, row.json)
                statement.setUtcTimestamp(3, now, utc)
                statement.setUtcTimestamp(4, now, utc)
            },
        )
    }

    private fun checkReadLimits(
        fields: Map<String, String>,
    ) {
        fields.forEach { (name, value) ->
            require(name.length <= MAX_FIELD_NAME_LENGTH) {
                "Field name exceeds read limit: length=${name.length}, max=$MAX_FIELD_NAME_LENGTH"
            }
            require(value.length <= MAX_FIELD_VALUE_LENGTH) {
                "Field value exceeds read limit: length=${value.length}, max=$MAX_FIELD_VALUE_LENGTH"
            }
        }
    }

    private class SerializedNote(
        val userId: Long,
        val json: String,
    )

    companion object {
        // Hibernate가 이 노트를 읽을 때 쓰는 Jackson 2 기본 읽기 한도와 같아야 하며, 넘는 값을 저장하면 이후 조회가 계속 실패한다.
        // 요청 검증이 같은 한도를 400으로 거절하게 되면 이 검사와 상수를 지운다.
        private const val MAX_FIELD_VALUE_LENGTH = 20_000_000
        private const val MAX_FIELD_NAME_LENGTH = 50_000
    }
}
