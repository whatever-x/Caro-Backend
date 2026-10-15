package com.whatever.caro.card.internal.library

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LibrarySeedIntegrationTest :
    DescribeSpec({
        val mysql = MySQLContainer(DockerImageName.parse("mysql:8.4"))
        val seed = FileSystemResource("scripts/sql/library/free-decks.sql")

        fun execute(
            sql: String,
        ) {
            DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                connection.createStatement().use { statement -> statement.execute(sql) }
            }
        }

        fun query(
            sql: String,
        ): List<String> =
            DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(rows.getString(1))
                            }
                        }
                    }
                }
            }

        fun register(
            continueOnError: Boolean = false,
        ) {
            DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                ScriptUtils.executeSqlScript(connection, EncodedResource(seed), continueOnError, false, "--", ";", "/*", "*/")
            }
        }

        beforeSpec {
            mysql.start()
            DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                ScriptUtils.executeSqlScript(
                    connection,
                    FileSystemResource("src/main/resources/db/migration/card/V4__create_library_tables.sql"),
                )
            }
        }

        afterEach {
            execute("DELETE FROM library_cards")
            execute("DELETE FROM library_decks")
        }

        afterSpec { mysql.stop() }

        it("세 무료 덱을 게시 상태로 등록하고 카드 400장의 순서와 내용을 저장한다") {
            register()
            query("SELECT name FROM library_decks WHERE published = TRUE ORDER BY sort_order") shouldBe
                listOf("영어 기초 어휘 100", "토익 업무 어휘 200", "세계 수도 100개")
            query(
                """
                SELECT CONCAT(COUNT(*), ':', COUNT(DISTINCT position), ':', MIN(position), ':', MAX(position))
                FROM library_cards GROUP BY library_deck_id ORDER BY library_deck_id
                """.trimIndent(),
            ) shouldBe listOf("100:100:0:99", "200:200:0:199", "100:100:0:99")
            query("SELECT back FROM library_cards WHERE front = 'résumé'") shouldBe listOf("이력서")
            query("SELECT back FROM library_cards WHERE front = '대한민국'") shouldBe listOf("서울특별시")
            query("SELECT COUNT(*) FROM library_decks WHERE CHAR_LENGTH(description) <= 500") shouldBe listOf("3")
            listOf("english-basic-100" to "영어 기초 어휘 100", "toeic-work-200" to "토익 업무 어휘 200").forEach { (key, name) ->
                val expectedCards = FileSystemResource("scripts/sql/library/content/$key.tsv")
                    .inputStream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
                    .drop(1).mapIndexed { position, line ->
                        val fields = line.split('\t')
                        "$position\t${fields[1]}\t${fields[2]}"
                    }
                query(
                    """
                    SELECT CONCAT(card.position, CHAR(9), card.front, CHAR(9), card.back)
                    FROM library_cards card JOIN library_decks deck ON deck.id = card.library_deck_id
                    WHERE deck.name = '$name' ORDER BY card.position
                    """.trimIndent(),
                ) shouldBe expectedCards
            }
        }

        it("재실행은 ID, 게시 종료와 운영자가 수정한 설명을 유지한다") {
            register()
            val ids = query("SELECT id FROM library_decks ORDER BY id")
            execute("UPDATE library_decks SET published = FALSE, description = '운영자 설명' WHERE sort_order = 10")
            register()
            query("SELECT id FROM library_decks ORDER BY id") shouldBe ids
            query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("400")
            query("SELECT description FROM library_decks WHERE published = FALSE") shouldBe listOf("운영자 설명")
        }

        it("기존 수도 덱은 보존하고 없는 영어 및 토익 덱만 등록한다") {
            register()
            val capitalId = query("SELECT id FROM library_decks WHERE sort_order = 100")
            execute("DELETE FROM library_cards WHERE library_deck_id IN (SELECT id FROM library_decks WHERE sort_order < 100)")
            execute("DELETE FROM library_decks WHERE sort_order < 100")
            execute("UPDATE library_decks SET published = FALSE WHERE sort_order = 100")
            register()
            query("SELECT id FROM library_decks WHERE sort_order = 100 AND published = FALSE") shouldBe capitalId
            query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("400")
            query("SELECT COUNT(*) FROM library_decks WHERE published = TRUE") shouldBe listOf("2")
        }

        it("카드 내용이 다른 기존 덱은 덮어쓰지 않고 다른 새 덱 등록도 거부한다") {
            register()
            execute("UPDATE library_cards SET back = '검수한 뜻' WHERE front = 'study'")
            execute("DELETE FROM library_cards WHERE library_deck_id IN (SELECT id FROM library_decks WHERE sort_order = 20)")
            execute("DELETE FROM library_decks WHERE sort_order = 20")
            shouldThrowAny { register() }
            query("SELECT COUNT(*) FROM library_decks") shouldBe listOf("2")
            query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("200")
            query("SELECT back FROM library_cards WHERE front = 'study'") shouldBe listOf("검수한 뜻")
        }

        it("같은 이름의 기존 덱이 여러 개면 임의로 고르지 않는다") {
            register()
            execute(
                """
                INSERT INTO library_decks (name, description, published, sort_order, created_at, updated_at)
                SELECT name, description, published, sort_order, created_at, updated_at
                FROM library_decks WHERE sort_order = 10
                """.trimIndent(),
            )
            shouldThrowAny { register() }
            query("SELECT COUNT(*) FROM library_decks") shouldBe listOf("4")
            query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("400")
        }

        it("카드 저장 실패 시 새 덱과 카드 등록을 모두 rollback한다") {
            execute("ALTER TABLE library_cards ADD CONSTRAINT reject_library_seed CHECK (position < 150)")
            try {
                shouldThrowAny { register() }
                query("SELECT COUNT(*) FROM library_decks") shouldBe listOf("0")
                query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("0")
            } finally {
                execute("ALTER TABLE library_cards DROP CHECK reject_library_seed")
            }
        }

        it("SQL 오류 뒤 계속 실행하는 환경에서도 불완전한 등록을 rollback한다") {
            execute("ALTER TABLE library_cards ADD CONSTRAINT reject_library_seed CHECK (position < 150)")
            try {
                register(continueOnError = true)
                query("SELECT COUNT(*) FROM library_decks") shouldBe listOf("0")
                query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("0")
            } finally {
                execute("ALTER TABLE library_cards DROP CHECK reject_library_seed")
            }
        }

        it("동시 등록을 직렬화해 같은 무료 덱과 카드를 중복 생성하지 않는다") {
            val pool = Executors.newFixedThreadPool(2)
            try {
                val registrations = List(2) { pool.submit { register() } }
                registrations.forEach { it.get(60, TimeUnit.SECONDS) }
                query("SELECT COUNT(*) FROM library_decks") shouldBe listOf("3")
                query("SELECT COUNT(*) FROM library_cards") shouldBe listOf("400")
            } finally {
                pool.shutdownNow()
            }
        }
    })
