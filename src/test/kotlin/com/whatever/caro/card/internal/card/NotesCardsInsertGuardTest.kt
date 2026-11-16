package com.whatever.caro.card.internal.card

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

// 1.1 묶음 저장은 드라이버가 계산한 키를 그대로 쓰므로, notes·cards의 id 할당 연속성을 깨는 SQL이 저장소에 들어오면 실패한다.
// 텍스트 검사라 운영자가 직접 실행하는 SQL과 문자열을 이어 붙이거나 문자열 템플릿(`$table`)으로 만든 SQL은 잡지 못한다.
class NotesCardsInsertGuardTest :
    DescribeSpec({

        describe("src/main의 코드와 마이그레이션") {
            it("notes·cards 대상 bulk insert와 mixed-mode insert, auto_increment_increment 변경이 없다") {
                val kotlinSources = File("src/main/kotlin").walk().filter { it.extension == "kt" }.toList()
                val sqlSources = File("src/main/resources/db").walk().filter { it.extension == "sql" }.toList()

                val violations = (kotlinSources + sqlSources).flatMap { file ->
                    findForbiddenSql(file.readText(), sqlFile = file.extension == "sql").map { line -> "${file.path}:$line" }
                }

                kotlinSources.shouldNotBeEmpty()
                sqlSources.shouldNotBeEmpty()
                violations.shouldBeEmpty()
            }
        }

        describe("findForbiddenSql") {
            it("bulk insert, mixed-mode insert, 컬럼 목록 생략 INSERT, 증가값 변경 예시를 잡는다") {
                val forbiddenKotlin = listOf(
                    "\"INSERT INTO notes (user_id, fields) SELECT user_id, fields FROM notes\"",
                    "\"\"\"\n    INSERT INTO `cards`\n    SELECT * FROM cards_backup\n\"\"\"",
                    "\"insert into Note (userId, fields) select n.userId, n.fields from Note n\"",
                    "\"REPLACE INTO notes (user_id, fields) VALUES (1, '{}')\"",
                    "\"LOAD DATA LOCAL INFILE '/tmp/notes.csv' INTO TABLE notes\"",
                    "\"INSERT INTO cards (card_template_id, note_id) VALUES (1, 1) ON DUPLICATE KEY UPDATE note_id = note_id\"",
                    "\"INSERT INTO notes (id, user_id, fields) VALUES (10, 1, '{}')\"",
                    "\"INSERT IGNORE INTO caro.notes (`id`, user_id) VALUES (10, 1)\"",
                    "\"INSERT INTO notes VALUES (10, 1, '{}', NOW(6), NOW(6), NULL)\"",
                    "\"SET SESSION auto_increment_increment = 2\"",
                    "\"jdbc:mysql://db/caro?sessionVariables=auto_increment_increment=2\"",
                )
                val forbiddenSql = listOf(
                    "INSERT INTO `notes` (user_id, fields)\nVALUES (1, \"{}\")\nON DUPLICATE KEY UPDATE user_id = user_id;",
                    "INSERT INTO `cards`\nVALUE (10, 1, 1, 1, 1, NOW(6), NOW(6), NULL);",
                    "SET @@auto_increment_increment = 2;",
                )

                forbiddenKotlin.forAll { findForbiddenSql(it, sqlFile = false).shouldNotBeEmpty() }
                forbiddenSql.forAll { findForbiddenSql(it, sqlFile = true).shouldNotBeEmpty() }
            }

            it("다중 행 VALUES INSERT와 다른 테이블의 ON DUPLICATE KEY UPDATE 시드는 통과시킨다") {
                val allowedKotlin = listOf(
                    "\"INSERT INTO notes (user_id, fields, created_at, updated_at, deleted_at) VALUES (?, CAST(? AS JSON), ?, ?, NULL)\"",
                    "\"INSERT INTO cards (card_template_id, note_id, deck_id, user_id, created_at, updated_at, deleted_at) VALUES \"",
                    "\"SELECT id FROM notes WHERE user_id = ?\"",
                    "// 세션의 auto_increment_increment를 바꾸지 않는다",
                )
                val allowedSql = listOf(
                    "INSERT INTO `note_types` (id, name, description)\nVALUES (1, 'front-back', \"설명\")\nON DUPLICATE KEY UPDATE id = id;",
                    "INSERT INTO `card_templates` (id, note_type_id) VALUES (1, 1) ON DUPLICATE KEY UPDATE id = id;",
                )

                allowedKotlin.forAll { findForbiddenSql(it, sqlFile = false).shouldBeEmpty() }
                allowedSql.forAll { findForbiddenSql(it, sqlFile = true).shouldBeEmpty() }
            }
        }
    })

private const val TARGET = """(?:\w+\.)?`?(?:notes|cards|note|card)`?(?![\w`])"""
private val insertHead = Regex(
    """\bINSERT\s+(?:(?:LOW_PRIORITY|HIGH_PRIORITY|DELAYED|IGNORE)\s+)*(?:INTO\s+)?$TARGET""",
    RegexOption.IGNORE_CASE,
)
private val replaceHead = Regex("""\bREPLACE\s+(?:(?:LOW_PRIORITY|DELAYED)\s+)?(?:INTO\s+)?$TARGET""", RegexOption.IGNORE_CASE)
private val loadDataHead = Regex("""\bLOAD\s+(?:DATA|XML)\b""", RegexOption.IGNORE_CASE)
private val loadDataTarget = Regex("""\bINTO\s+TABLE\s+$TARGET""", RegexOption.IGNORE_CASE)
private val incrementChange = Regex("""auto_increment_increment\s*:?=""", RegexOption.IGNORE_CASE)
private val insertSelect = Regex("""\bSELECT\b""", RegexOption.IGNORE_CASE)
private val onDuplicateKey = Regex("""\bON\s+DUPLICATE\s+KEY\s+UPDATE\b""", RegexOption.IGNORE_CASE)
private val valuesWithoutColumns = Regex("""^\s*VALUES?\b""", RegexOption.IGNORE_CASE)
private val columnList = Regex("""^\s*\(([^)]*)\)""")
private val insertSetId = Regex("""^\s*SET\b.*?(?<![\w.])`?id`?\s*=""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

// Kotlin 파일은 따옴표에서 끊어야 뒤따르는 다른 문자열의 SELECT를 이 INSERT의 위반으로 잡지 않고, 그 대신 문자열 안 이스케이프된 따옴표 뒤의 절은 놓친다.
private fun statementAfter(
    text: String,
    from: Int,
    sqlFile: Boolean,
): String {
    val terminators = if (sqlFile) charArrayOf(';') else charArrayOf(';', '"')
    val end = text.indexOfAny(terminators, startIndex = from).takeIf { it >= 0 } ?: text.length
    return text.substring(from, end)
}

private fun hasIdColumn(
    statementTail: String,
): Boolean {
    val columns = columnList.find(statementTail)?.groupValues?.get(1) ?: return false
    return columns.split(",").any { it.trim().trim('`').equals("id", ignoreCase = true) }
}

private fun findForbiddenSql(
    text: String,
    sqlFile: Boolean,
): List<Int> {
    val insertViolations = insertHead.findAll(text).filter { match ->
        val tail = statementAfter(text, match.range.last + 1, sqlFile)
        insertSelect.containsMatchIn(tail) ||
            onDuplicateKey.containsMatchIn(tail) ||
            hasIdColumn(tail) ||
            valuesWithoutColumns.containsMatchIn(tail) ||
            insertSetId.containsMatchIn(tail)
    }
    val loadDataViolations = loadDataHead.findAll(text).filter { match ->
        loadDataTarget.containsMatchIn(statementAfter(text, match.range.last + 1, sqlFile))
    }
    val offsets = (insertViolations + replaceHead.findAll(text) + loadDataViolations + incrementChange.findAll(text))
        .map { it.range.first }
    return offsets.map { offset -> text.substring(0, offset).count { it == '\n' } + 1 }.distinct().sorted().toList()
}
