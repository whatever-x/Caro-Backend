package com.whatever.caro.common.jdbc

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.QueryTimeoutException
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.support.KeyHolder
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator
import java.sql.BatchUpdateException
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.SQLTimeoutException
import java.sql.Statement
import java.util.Calendar

class MultiRowInsertUnitTest :
    DescribeSpec({

        val rows = listOf("a", "b", "c")
        val noInfo = IntArray(rows.size) { Statement.SUCCESS_NO_INFO }

        fun answerBatch(
            setter: BatchPreparedStatementSetter,
            boundRows: MutableList<String>,
        ) {
            val statement = mockk<PreparedStatement>(relaxed = true)
            every { statement.setString(1, any()) } answers { boundRows += secondArg<String>() }
            repeat(setter.batchSize) { setter.setValues(statement, it) }
        }

        fun jdbcTemplateReturning(
            updateCounts: IntArray,
            keys: List<Long> = emptyList(),
            boundRows: MutableList<String> = mutableListOf(),
        ): JdbcTemplate {
            val jdbcTemplate = mockk<JdbcTemplate>()
            every {
                jdbcTemplate.batchUpdate(any<PreparedStatementCreator>(), any<BatchPreparedStatementSetter>(), any<KeyHolder>())
            } answers {
                answerBatch(secondArg(), boundRows)
                thirdArg<KeyHolder>().keyList.addAll(keys.map { mapOf<String, Any>("GENERATED_KEY" to it) })
                updateCounts
            }
            every { jdbcTemplate.batchUpdate(any<String>(), any<BatchPreparedStatementSetter>()) } answers {
                answerBatch(secondArg(), boundRows)
                updateCounts
            }
            return jdbcTemplate
        }

        fun multiRowInsert(
            jdbcTemplate: JdbcTemplate,
        ) = MultiRowInsert(jdbcTemplate = jdbcTemplate, table = "notes", columns = listOf("user_id"), rowValues = "(?)")

        val bindFirstParameter: (PreparedStatement, String, Calendar) -> Unit = { statement, row, _ ->
            statement.setString(1, row)
        }

        describe("insertReturningKeys") {
            it("rewrite된 배치의 SUCCESS_NO_INFO와 행 수만큼의 키를 받으면 키를 그대로 돌려준다") {
                val jdbcTemplate = jdbcTemplateReturning(updateCounts = noInfo, keys = listOf(10L, 11L, 12L))

                multiRowInsert(jdbcTemplate).insertReturningKeys(rows, bindFirstParameter) shouldBe listOf(10L, 11L, 12L)
            }

            it("bindRow는 배치 순서대로 각 행을 첫 파라미터부터 바인딩한다") {
                val boundRows = mutableListOf<String>()
                val jdbcTemplate = jdbcTemplateReturning(updateCounts = noInfo, keys = listOf(10L, 11L, 12L), boundRows = boundRows)

                multiRowInsert(jdbcTemplate).insertReturningKeys(rows, bindFirstParameter)

                boundRows shouldBe rows
            }

            it("드라이버가 돌려준 키 개수가 행 수와 다르면 IllegalStateException을 던진다") {
                val jdbcTemplate = jdbcTemplateReturning(updateCounts = noInfo, keys = listOf(10L, 11L))

                val error = shouldThrow<IllegalStateException> {
                    multiRowInsert(jdbcTemplate).insertReturningKeys(rows, bindFirstParameter)
                }

                error.message shouldContain "expected=3"
                error.message shouldContain "actual=2"
            }

            it("update count에 실패한 행이 있으면 IllegalStateException을 던진다") {
                val jdbcTemplate = jdbcTemplateReturning(
                    updateCounts = intArrayOf(1, Statement.EXECUTE_FAILED, 1),
                    keys = listOf(10L, 11L, 12L),
                )

                val error = shouldThrow<IllegalStateException> {
                    multiRowInsert(jdbcTemplate).insertReturningKeys(rows, bindFirstParameter)
                }

                error.message shouldContain "${Statement.EXECUTE_FAILED}"
            }

            it("빈 입력은 JdbcTemplate을 호출하지 않고 빈 목록을 돌려준다") {
                val jdbcTemplate = jdbcTemplateReturning(updateCounts = IntArray(0))

                multiRowInsert(jdbcTemplate).insertReturningKeys(emptyList(), bindFirstParameter).shouldBeEmpty()

                verify(exactly = 0) {
                    jdbcTemplate.batchUpdate(any<PreparedStatementCreator>(), any<BatchPreparedStatementSetter>(), any<KeyHolder>())
                }
            }
        }

        describe("insert") {
            it("행마다 1이 돌아오는 rewrite 없는 배치도 예외 없이 끝난다") {
                shouldNotThrowAny {
                    multiRowInsert(jdbcTemplateReturning(updateCounts = intArrayOf(1, 1, 1))).insert(rows, bindFirstParameter)
                }
            }

            it("update count 개수가 행 수와 다르면 IllegalStateException을 던진다") {
                val error = shouldThrow<IllegalStateException> {
                    multiRowInsert(jdbcTemplateReturning(updateCounts = intArrayOf(1, 1))).insert(rows, bindFirstParameter)
                }

                error.message shouldContain "rows=3"
                error.message shouldContain "counts=2"
            }

            it("빈 입력은 JdbcTemplate을 호출하지 않는다") {
                val jdbcTemplate = jdbcTemplateReturning(updateCounts = IntArray(0))

                multiRowInsert(jdbcTemplate).insert(emptyList(), bindFirstParameter)

                verify(exactly = 0) { jdbcTemplate.batchUpdate(any<String>(), any<BatchPreparedStatementSetter>()) }
            }
        }

        describe("배치 실패 변환") {
            fun jdbcTemplateThrowing(
                failure: SQLException,
            ): JdbcTemplate {
                val jdbcTemplate = mockk<JdbcTemplate>()
                every { jdbcTemplate.batchUpdate(any<String>(), any<BatchPreparedStatementSetter>()) } throws
                    UncategorizedSQLException("PreparedStatementCallback", "INSERT", failure)
                every { jdbcTemplate.exceptionTranslator } returns SQLExceptionSubclassTranslator()
                return jdbcTemplate
            }

            it("드라이버가 BatchUpdateException으로 감싼 timeout은 원래 예외를 suppressed로 담은 QueryTimeoutException으로 던진다") {
                val wrapped = BatchUpdateException("cancelled", null, 0, IntArray(0), SQLTimeoutException("cancelled"))

                val error = shouldThrow<QueryTimeoutException> {
                    multiRowInsert(jdbcTemplateThrowing(wrapped)).insert(rows, bindFirstParameter)
                }

                error.suppressed.single().shouldBeInstanceOf<UncategorizedSQLException>()
            }

            it("BatchUpdateException이 아닌 미분류 예외는 그대로 던진다") {
                shouldThrow<UncategorizedSQLException> {
                    multiRowInsert(jdbcTemplateThrowing(SQLException("unknown"))).insert(rows, bindFirstParameter)
                }
            }
        }
    })
