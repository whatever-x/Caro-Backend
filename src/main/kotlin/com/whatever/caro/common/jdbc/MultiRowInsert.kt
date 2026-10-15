package com.whatever.caro.common.jdbc

import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.sql.BatchUpdateException
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

// JDBC URL에 rewriteBatchedStatements=true가 없으면 드라이버가 행마다 INSERT를 보내므로 넣어야함
class MultiRowInsert(
    private val jdbcTemplate: JdbcTemplate,
    private val table: String,
    columns: List<String>,
    rowValues: String,
) {
    private val sql = "INSERT INTO $table (${columns.joinToString(", ")}) VALUES $rowValues"

    fun <T> insertReturningKeys(
        rows: List<T>,
        bindRow: (PreparedStatement, T, Calendar) -> Unit,
    ): List<Long> {
        if (rows.isEmpty()) {
            return emptyList()
        }
        val keyHolder = GeneratedKeyHolder()
        val updateCounts = translatingBatchFailure {
            jdbcTemplate.batchUpdate(
                { connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) },
                batchSetter(rows, bindRow),
                keyHolder,
            )
        }
        return checkGeneratedKeys(
            table = table,
            rowCount = rows.size,
            updateCounts = updateCounts,
            keys = keyHolder.keyList.map { it.values.single() },
        )
    }

    fun <T> insert(
        rows: List<T>,
        bindRow: (PreparedStatement, T, Calendar) -> Unit,
    ) {
        if (rows.isEmpty()) {
            return
        }
        val updateCounts = translatingBatchFailure { jdbcTemplate.batchUpdate(sql, batchSetter(rows, bindRow)) }
        checkUpdateCounts(table = table, rowCount = rows.size, updateCounts = updateCounts)
    }

    // 드라이버가 timeout 예외를 SQLState와 error code 없는 BatchUpdateException으로 감싸 Spring이 QueryTimeoutException으로 변환하지 못한다.
    private fun <R> translatingBatchFailure(
        batch: () -> R,
    ): R =
        try {
            batch()
        } catch (e: UncategorizedSQLException) {
            val driverFailure = (e.sqlException as? BatchUpdateException)?.cause as? SQLException ?: throw e
            throw jdbcTemplate.exceptionTranslator.translate("$table batch INSERT", sql, driverFailure)?.also {
                it.addSuppressed(e)
            }
                ?: e
        }

    private fun <T> batchSetter(
        rows: List<T>,
        bindRow: (PreparedStatement, T, Calendar) -> Unit,
    ): BatchPreparedStatementSetter {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return object : BatchPreparedStatementSetter {
            override fun setValues(
                ps: PreparedStatement,
                i: Int,
            ) = bindRow(ps, rows[i], utc)

            override fun getBatchSize(): Int = rows.size
        }
    }
}

// Hibernate가 Instant를 바인딩하는 호출과 같아야 URL의 connectionTimeZone과 무관하게 JPA로 저장한 행과 같은 UTC 기준 DATETIME 값이 저장된다.
fun PreparedStatement.setUtcTimestamp(
    index: Int,
    instant: Instant,
    utc: Calendar,
) {
    setTimestamp(index, Timestamp.from(instant), utc)
}

// rewrite된 다중 행 INSERT는 행마다 영향 행 수 대신 SUCCESS_NO_INFO를 돌려준다.
internal fun checkUpdateCounts(
    table: String,
    rowCount: Int,
    updateCounts: IntArray,
) {
    check(updateCounts.size == rowCount && updateCounts.all { it == 1 || it == Statement.SUCCESS_NO_INFO }) {
        "$table INSERT update counts do not match rows: rows=$rowCount, counts=${updateCounts.size}, values=${updateCounts.distinct()}"
    }
}

// 드라이버가 LAST_INSERT_ID에 증가값을 더해 계산한 키라 실제 id와 어긋나도 이 검사는 통과한다.
internal fun checkGeneratedKeys(
    table: String,
    rowCount: Int,
    updateCounts: IntArray,
    keys: List<Any?>,
): List<Long> {
    checkUpdateCounts(table = table, rowCount = rowCount, updateCounts = updateCounts)
    check(keys.size == rowCount) {
        "$table INSERT generated key count does not match rows: expected=$rowCount, actual=${keys.size}"
    }
    return keys.map { (checkNotNull(it) as Number).toLong() }
}
