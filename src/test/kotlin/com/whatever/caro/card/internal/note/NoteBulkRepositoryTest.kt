package com.whatever.caro.card.internal.note

import com.fasterxml.jackson.core.exc.StreamConstraintsException
import com.whatever.caro.CaroModuleTest
import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.card.internal.card.CardRepository
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.flywaydb.core.Flyway
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.dao.QueryTimeoutException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionTimedOutException
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@CaroModuleTest(extraIncludes = ["common"])
class NoteBulkRepositoryTest(
    private val noteRepository: NoteRepository,
    private val cardRepository: CardRepository,
    private val transactionTemplate: TransactionTemplate,
    private val transactionManager: PlatformTransactionManager,
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val dataSource: DataSource,
    private val clock: Clock,
) : DescribeSpec({

    val userId = 7_001L
    val utcDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)

    // 앞뒤 각 500자 한글·한자로, 문자마다 UTF-8 3바이트라 실제 바이트가 추정치에 가장 가까운 1.1 요청 본문이다.
    fun worstCaseRow(
        index: Int,
        owner: Long = userId,
    ) = NoteInsertRow(userId = owner, fields = mapOf("front" to "$index " + "가漢".repeat(250), "back" to "漢가".repeat(250)))

    fun row(
        front: String,
        owner: Long = userId,
    ) = NoteInsertRow(userId = owner, fields = mapOf("front" to front, "back" to "뒷면 $front"))

    fun sessionStatus(
        template: JdbcTemplate,
        name: String,
    ): Long = template.queryForObject("SHOW SESSION STATUS LIKE ?", { rs, _ -> rs.getLong("Value") }, name)!!

    fun storedIdsAndFronts(
        owner: Long,
    ): List<Pair<Long, String>> =
        jdbcTemplate.query(
            "SELECT id, fields->>'$.front' AS front FROM notes WHERE user_id = ? ORDER BY id",
            { rs, _ -> rs.getLong("id") to rs.getString("front") },
            owner,
        )

    // 컬럼 목록을 고정하지 않아야 나중에 추가된 컬럼도 JPA 저장값과 비교된다.
    fun storedColumns(
        id: Long,
    ): Map<String, Any?> = jdbcTemplate.queryForMap("SELECT * FROM notes WHERE id = ?", id) - setOf("id", "created_at", "updated_at")

    fun storedTimes(
        id: Long,
    ): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at,
                   DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at,
                   deleted_at
            FROM notes WHERE id = ?
            """.trimIndent(),
            id,
        )

    fun jdbcUrlWith(
        baseUrl: String,
        params: Map<String, String>,
    ): String {
        val base = baseUrl.substringBefore("?")
        val existing = baseUrl.substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .filter { it.isNotBlank() }
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        return base + "?" + (existing + params).entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    // 단일 연결은 같은 MySQL 컨테이너에 URL 옵션만 바꿔 연결하므로 Spring 컨텍스트가 늘지 않는다.
    fun <T> withSingleConnection(
        params: Map<String, String>,
        block: (NoteBulkRepository, JdbcTemplate, TransactionTemplate) -> T,
    ): T {
        val hikari = dataSource.unwrap(HikariDataSource::class.java)
        val single = SingleConnectionDataSource(jdbcUrlWith(hikari.jdbcUrl, params), hikari.username, hikari.password, true)
        try {
            val template = JdbcTemplate(single)
            return block(NoteBulkRepositoryImpl(template), template, TransactionTemplate(DataSourceTransactionManager(single)))
        } finally {
            single.destroy()
        }
    }

    beforeTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
    }

    afterSpec {
        noteRepository.deleteAllInBatch()
    }

    describe("insertAll 저장값") {
        it("JDBC로 저장한 노트를 Hibernate가 입력과 같은 Map으로 읽고, 같은 Map을 JPA로 저장한 노트와 JSON 값이 같다") {
            val fields = mapOf(
                "front" to "한글 漢字 \uD83D\uDE00 \u0000\u0001\u001F\u007F\t\n\r \"따옴표\" \\백슬래시 /슬래시 'apostrophe' ?물음 %퍼센트 ",
                "back" to "",
                "이름 \"따옴표\" / \\ ' ? %" to "특수문자 이름",
            )

            val jdbcId = transactionTemplate.execute {
                noteRepository.insertAll(listOf(NoteInsertRow(userId = userId, fields = fields)), Instant.now(clock))
            }!!.single()
            val jpaId = noteRepository.save(Note(userId = userId, fields = fields)).id

            noteRepository.findById(jdbcId).get().fields shouldBe fields
            val storedJson = jdbcTemplate.queryForMap(
                "SELECT (SELECT CAST(fields AS CHAR) FROM notes WHERE id = ?) AS jdbc, (SELECT CAST(fields AS CHAR) FROM notes WHERE id = ?) AS jpa",
                jdbcId,
                jpaId,
            )
            storedJson["jdbc"] shouldBe storedJson["jpa"]
        }

        it("JPA로 저장한 노트와 id, created_at, updated_at을 뺀 모든 컬럼이 같다") {
            val fields = mapOf("front" to "앞면", "back" to "뒷면")
            val jpaId = noteRepository.saveAll(listOf(Note(userId = userId, fields = fields))).single().id

            val jdbcId = transactionTemplate.execute {
                noteRepository.insertAll(listOf(NoteInsertRow(userId = userId, fields = fields)), Instant.now(clock))
            }!!.single()

            storedColumns(jdbcId) shouldBe storedColumns(jpaId)
        }

        it("created_at과 updated_at은 now의 UTC 기준 DATETIME 값이고, JPA로 읽은 시각이 now와 같으며 deleted_at은 NULL이다") {
            val now = Instant.now(clock)

            val id = transactionTemplate.execute { noteRepository.insertAll(listOf(row("시각")), now) }!!.single()

            val times = storedTimes(id)
            times["created_at"] shouldBe utcDateTime.format(now)
            times["updated_at"] shouldBe utcDateTime.format(now)
            times["deleted_at"] shouldBe null
            val note = noteRepository.findById(id).get()
            note.createdAt shouldBe now
            note.updatedAt shouldBe now
        }

        it("connectionTimeZone이 Asia/Seoul인 연결에서도 created_at이 UTC 기준 DATETIME 값이다") {
            val now = Instant.now(clock)
            val seoulParams = mapOf("connectionTimeZone" to "Asia/Seoul", "forceConnectionTimeZoneToSession" to "true")

            val (sessionTimeZone, id) = withSingleConnection(seoulParams) { repository, template, transaction ->
                val zone = template.queryForObject("SELECT @@session.time_zone", String::class.java)
                zone to transaction.execute { repository.insertAll(listOf(row("서울 연결")), now) }!!.single()
            }

            sessionTimeZone shouldBe "Asia/Seoul"
            storedTimes(id)["created_at"] shouldBe utcDateTime.format(now)
            noteRepository.findById(id).get().createdAt shouldBe now
        }
    }

    describe("insertAll 길이 한도") {
        fun insertsWhileRejecting(
            fields: Map<String, String>,
        ): Long =
            transactionTemplate.execute { status ->
                val before = sessionStatus(jdbcTemplate, "Com_insert")
                // 레포지토리 proxy가 Impl의 IllegalArgumentException을 변환하며, 처리하는 핸들러가 없어 500 C999가 된다.
                shouldThrow<InvalidDataAccessApiUsageException> {
                    noteRepository.insertAll(listOf(NoteInsertRow(userId = userId, fields = fields)), Instant.now(clock))
                }.cause.shouldBeInstanceOf<IllegalArgumentException>()
                status.setRollbackOnly()
                sessionStatus(jdbcTemplate, "Com_insert") - before
            }!!

        it("20,000,001자 값은 SQL 실행 전에 거절되고 행이 남지 않는다") {
            insertsWhileRejecting(mapOf("front" to "a".repeat(20_000_001))) shouldBe 0L
            noteRepository.count() shouldBe 0L
        }

        it("50,001자 이름은 SQL 실행 전에 거절되고 행이 남지 않는다") {
            insertsWhileRejecting(mapOf("n".repeat(50_001) to "값")) shouldBe 0L
            noteRepository.count() shouldBe 0L
        }

        it("50,000자 이름은 저장되고 Hibernate가 읽는다") {
            val name = "n".repeat(50_000)

            val id = transactionTemplate.execute {
                noteRepository.insertAll(listOf(NoteInsertRow(userId = userId, fields = mapOf(name to "값"))), Instant.now(clock))
            }!!.single()

            noteRepository.findById(id).get().fields shouldBe mapOf(name to "값")
        }

        it("SQL로 직접 넣은 50,001자 이름 노트는 Hibernate가 Jackson 읽기 한도로 읽지 못한다") {
            jdbcTemplate.update("INSERT INTO notes (user_id, fields) VALUES (?, JSON_OBJECT(?, '값'))", userId, "n".repeat(50_001))
            val id = jdbcTemplate.queryForObject("SELECT id FROM notes WHERE user_id = ?", Long::class.java, userId)!!

            val error = shouldThrowAny { noteRepository.findById(id) }

            generateSequence<Throwable>(error) { it.cause }.map { it::class }.toList() shouldContain
                StreamConstraintsException::class
        }

        it("Hibernate는 JSON 컬럼을 Jackson 2 JacksonJsonFormatMapper로 읽는다") {
            entityManagerFactory.unwrap(SessionFactoryImplementor::class.java)
                .sessionFactoryOptions.jsonFormatMapper
                .shouldBeInstanceOf<JacksonJsonFormatMapper>()
        }
    }

    describe("insertAll 키 순서") {
        it("1,000행의 키가 auto_increment_increment 간격이고 id 순서가 입력 순서와 같다") {
            val fronts = (1..1000).map { "단어$it" }
            val increment = jdbcTemplate.queryForObject("SELECT @@auto_increment_increment", Long::class.java)

            val keys = transactionTemplate.execute { noteRepository.insertAll(fronts.map { row(it) }, Instant.now(clock)) }!!

            keys.zipWithNext { previous, next -> next - previous }.toSet() shouldBe setOf(increment)
            storedIdsAndFronts(userId) shouldBe keys.zip(fronts)
        }

        it("최악 본문 1,000행은 서버 max_allowed_packet 안이라 INSERT 한 문장으로 저장되고 id 순서가 입력 순서와 같다") {
            val rows = (1..1000).map { worstCaseRow(it) }

            val (inserts, keys) = transactionTemplate.execute {
                val before = sessionStatus(jdbcTemplate, "Com_insert")
                val keys = noteRepository.insertAll(rows, Instant.now(clock))
                (sessionStatus(jdbcTemplate, "Com_insert") - before) to keys
            }!!

            inserts shouldBe 1L
            storedIdsAndFronts(userId) shouldBe keys.zip(rows.map { it.fields.getValue("front") })
        }

        it("다중 행 INSERT 4개와 JPA 단건 INSERT가 동시에 실행돼도 각 다중 행 INSERT의 키가 연속이고 내용이 입력 순서대로 맞는다") {
            jdbcTemplate.queryForObject("SELECT @@innodb_autoinc_lock_mode", Int::class.java) shouldBe 2
            val executor = Executors.newFixedThreadPool(5)
            try {
                repeat(20) { round ->
                    val barrier = CyclicBarrier(5)
                    val bulkInserts = (0 until 4).map { thread ->
                        val owner = 100_000L + round * 10 + thread
                        val fronts = (1..1000).map { "r$round-t$thread-$it" }
                        val future = executor.submit<List<Long>> {
                            barrier.await()
                            transactionTemplate.execute { noteRepository.insertAll(fronts.map { row(it, owner) }, Instant.now(clock)) }
                        }
                        Triple(owner, fronts, future)
                    }
                    val singleInserts = executor.submit<Unit> {
                        barrier.await()
                        repeat(50) { noteRepository.save(Note(userId = 99_999L, fields = mapOf("front" to "단건", "back" to "단건"))) }
                    }

                    singleInserts.get(30, TimeUnit.SECONDS)
                    bulkInserts.forEach { (owner, fronts, future) ->
                        val keys = future.get(30, TimeUnit.SECONDS)
                        keys.zipWithNext { previous, next -> next - previous }.toSet() shouldBe setOf(1L)
                        storedIdsAndFronts(owner) shouldBe keys.zip(fronts)
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }

        it("sessionVariables로 auto_increment_increment가 2인 연결에서 키 간격이 2이고 실제 id와 내용 순서가 같다") {
            val fronts = (1..1000).map { "간격$it" }

            val keys = withSingleConnection(mapOf("sessionVariables" to "auto_increment_increment=2")) { repository, _, transaction ->
                transaction.execute { repository.insertAll(fronts.map { row(it) }, Instant.now(clock)) }!!
            }

            keys.zipWithNext { previous, next -> next - previous }.toSet() shouldBe setOf(2L)
            storedIdsAndFronts(userId) shouldBe keys.zip(fronts)
        }

        it("useServerPrepStmts=true 연결에서도 1,000행의 id 순서가 입력 순서와 같다") {
            val fronts = (1..1000).map { "서버$it" }

            val keys = withSingleConnection(mapOf("useServerPrepStmts" to "true")) { repository, _, transaction ->
                transaction.execute { repository.insertAll(fronts.map { row(it) }, Instant.now(clock)) }!!
            }

            storedIdsAndFronts(userId) shouldBe keys.zip(fronts)
        }

        it("서버 max_allowed_packet이 1MiB면 최악 본문 1,000행이 여러 문장으로 나뉘어 순서대로 저장된다") {
            // 공유 컨테이너의 전역 값을 바꾸면 그 사이 풀에 생긴 연결이 1MiB 한도를 계속 가져 뒤 스펙의 큰 문장이 PacketTooBigException으로 실패하므로 따로 띄운다.
            MySQLContainer(DockerImageName.parse(TestcontainersConfiguration.MYSQL_VERSION))
                .withCommand("--max-allowed-packet=1048576")
                .withUrlParam("rewriteBatchedStatements", "true")
                .use { mysql ->
                    mysql.start()
                    Flyway.configure()
                        .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                        .locations("classpath:db/migration/card")
                        .load()
                        .migrate()
                    val single = SingleConnectionDataSource(mysql.jdbcUrl, mysql.username, mysql.password, true)
                    try {
                        val template = JdbcTemplate(single)
                        val repository = NoteBulkRepositoryImpl(template)
                        val rows = (1..1000).map { worstCaseRow(it) }

                        val (inserts, keys) = TransactionTemplate(DataSourceTransactionManager(single)).execute {
                            val before = sessionStatus(template, "Com_insert")
                            val keys = repository.insertAll(rows, Instant.now(clock))
                            (sessionStatus(template, "Com_insert") - before) to keys
                        }!!

                        template.queryForObject("SELECT @@max_allowed_packet", Long::class.java) shouldBe 1_048_576L
                        inserts shouldBeInRange 2L..10L
                        template.query(
                            "SELECT id, fields->>'$.front' AS front FROM notes ORDER BY id",
                        ) { rs, _ -> rs.getLong("id") to rs.getString("front") } shouldBe
                            keys.zip(rows.map { it.fields.getValue("front") })
                    } finally {
                        single.destroy()
                    }
                }
        }
    }

    describe("insertAll 트랜잭션") {
        it("트랜잭션 밖에서 부르면 IllegalTransactionStateException을 던지고 저장하지 않는다") {
            shouldThrow<IllegalTransactionStateException> {
                noteRepository.insertAll(listOf(row("트랜잭션 밖")), Instant.now(clock))
            }

            noteRepository.count() shouldBe 0L
        }

        it("트랜잭션 안의 JdbcTemplate과 EntityManager는 같은 커넥션을 쓴다") {
            val (jdbcConnectionId, jpaConnectionId) = transactionTemplate.execute {
                jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long::class.java) to
                    (entityManager.createNativeQuery("SELECT CONNECTION_ID()").singleResult as Number).toLong()
            }!!

            jdbcConnectionId shouldBe jpaConnectionId
        }

        it("다른 커넥션의 락에 막히면 트랜잭션 timeout에 QueryTimeoutException으로 끝나고 행이 남지 않는다") {
            val timeoutTemplate = TransactionTemplate(transactionManager).apply { timeout = 1 }
            val lockHolder = dataSource.connection
            try {
                lockHolder.autoCommit = false
                lockHolder.createStatement().use { it.executeQuery("SELECT id FROM notes FOR UPDATE").close() }
                val startedAt = System.nanoTime()

                shouldThrow<QueryTimeoutException> {
                    timeoutTemplate.execute { noteRepository.insertAll(listOf(row("락 대기")), Instant.now(clock)) }
                }

                Duration.ofNanos(System.nanoTime() - startedAt) shouldBeLessThan Duration.ofSeconds(5)
            } finally {
                lockHolder.rollback()
                lockHolder.close()
            }
            noteRepository.count() shouldBe 0L
        }

        it("트랜잭션 deadline이 지난 뒤에는 문장을 실행하기 전에 TransactionTimedOutException을 던진다") {
            val timeoutTemplate = TransactionTemplate(transactionManager).apply { timeout = 1 }

            shouldThrow<TransactionTimedOutException> {
                timeoutTemplate.execute {
                    Thread.sleep(1_100)
                    noteRepository.insertAll(listOf(row("deadline 이후")), Instant.now(clock))
                }
            }

            noteRepository.count() shouldBe 0L
        }

        it("빈 입력은 SQL을 실행하지 않고 빈 목록을 돌려준다") {
            val (statements, keys) = transactionTemplate.execute {
                val before = sessionStatus(jdbcTemplate, "Com_insert") + sessionStatus(jdbcTemplate, "Com_select")
                val keys = noteRepository.insertAll(emptyList(), Instant.now(clock))
                (sessionStatus(jdbcTemplate, "Com_insert") + sessionStatus(jdbcTemplate, "Com_select") - before) to keys
            }!!

            statements shouldBe 0L
            keys.shouldBeEmpty()
        }
    }
})
