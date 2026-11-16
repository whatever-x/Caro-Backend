package com.whatever.caro.common.jdbc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigInteger
import java.sql.Statement

class GeneratedKeyCheckUnitTest :
    DescribeSpec({

        val noInfo = intArrayOf(Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO)

        describe("checkUpdateCounts") {
            it("모든 값이 1이거나 SUCCESS_NO_INFO면 통과한다") {
                checkUpdateCounts(table = "notes", rowCount = 3, updateCounts = noInfo)
                checkUpdateCounts(table = "notes", rowCount = 3, updateCounts = intArrayOf(1, 1, 1))
            }

            it("update count 개수가 행 수와 다르면 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    checkUpdateCounts(table = "notes", rowCount = 3, updateCounts = intArrayOf(1, 1))
                }
            }

            it("0이나 EXECUTE_FAILED가 있으면 테이블과 값을 담아 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    checkUpdateCounts(table = "notes", rowCount = 3, updateCounts = intArrayOf(1, 0, 1))
                }
                val error = shouldThrow<IllegalStateException> {
                    checkUpdateCounts(table = "cards", rowCount = 2, updateCounts = intArrayOf(1, Statement.EXECUTE_FAILED))
                }

                error.message shouldContain "cards"
                error.message shouldContain "${Statement.EXECUTE_FAILED}"
            }
        }

        describe("checkGeneratedKeys") {
            it("update count가 맞지 않으면 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    checkGeneratedKeys(table = "notes", rowCount = 3, updateCounts = intArrayOf(1, 1), keys = listOf(1L, 2L, 3L))
                }
            }

            it("키 개수가 행 수와 다르면 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    checkGeneratedKeys(table = "notes", rowCount = 3, updateCounts = noInfo, keys = listOf(1L, 2L))
                }
            }

            it("null 키가 있으면 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    checkGeneratedKeys(table = "notes", rowCount = 3, updateCounts = noInfo, keys = listOf(1L, null, 3L))
                }
            }

            it("드라이버가 준 BigInteger 키를 Long으로 바꾼다") {
                checkGeneratedKeys(
                    table = "notes",
                    rowCount = 2,
                    updateCounts = intArrayOf(Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO),
                    keys = listOf(BigInteger.valueOf(10), BigInteger.valueOf(11)),
                ) shouldBe listOf(10L, 11L)
            }

            it("한 행이면 그 키를 그대로 돌려준다") {
                checkGeneratedKeys(table = "notes", rowCount = 1, updateCounts = intArrayOf(1), keys = listOf(BigInteger.valueOf(7))) shouldBe
                    listOf(7L)
            }
        }
    })
