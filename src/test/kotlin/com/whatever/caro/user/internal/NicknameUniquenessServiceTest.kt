package com.whatever.caro.user.internal

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.NicknameDuplicatedException
import com.whatever.caro.user.exception.UserErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CaroModuleTest(extraIncludes = ["common"])
class NicknameUniquenessServiceTest(
    private val service: UserService,
    private val users: UserRepository,
    private val socialAccounts: SocialAccountRepository,
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : DescribeSpec({
    val tx = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
    }

    afterTest {
        socialAccounts.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    it("DB는 대소문자와 뒤 공백을 같은 닉네임으로 비교한다") {
        users.saveAndFlush(User(nickname = "OwnedNick"))
        listOf("ownednick", "OwnedNick ").forEach { nickname ->
            shouldThrow<DataIntegrityViolationException> { users.saveAndFlush(User(nickname = nickname)) }
        }
        users.count() shouldBe 1L
    }

    it("탈퇴한 닉네임은 여러 계정이 순서대로 재사용할 수 있다") {
        val first = users.save(User(nickname = "ReusableNick"))
        service.deleteMe(first.id)
        val second = users.save(User(nickname = "reusablenick"))
        service.deleteMe(second.id)
        users.saveAndFlush(User(nickname = "ReusableNick"))
        users.count() shouldBe 3L
    }

    it("같은 소셜 계정의 중복 생성은 롤백 후 기존 계정을 반환한다") {
        val first = service.createSocialUser(SocialProvider.GOOGLE, "same-provider", null)
        val second = service.createSocialUser(SocialProvider.GOOGLE, "same-provider", null)
        second.id shouldBe first.id
        users.count() shouldBe 1L
        socialAccounts.count() shouldBe 1L
    }

    it("기존 중복이 있으면 마이그레이션은 데이터를 고치지 않고 실패한다") {
        jdbc.execute("CREATE TABLE nickname_migration_fixture (nickname VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, deleted_at DATETIME(6) NULL) ENGINE=InnoDB")
        try {
            jdbc.update("INSERT INTO nickname_migration_fixture (nickname) VALUES (?), (?)", "ExistingNick", "existingnick")
            val migration = ClassPathResource("db/migration/user/V8__unique_undeleted_nickname.sql")
                .inputStream.bufferedReader().use { it.readText() }
            shouldThrow<DataIntegrityViolationException> {
                jdbc.execute(migration.replace("`users`", "`nickname_migration_fixture`"))
            }
            jdbc.queryForList("SELECT nickname FROM nickname_migration_fixture ORDER BY BINARY nickname", String::class.java) shouldBe
                listOf("ExistingNick", "existingnick")
        } finally {
            jdbc.execute("DROP TABLE nickname_migration_fixture")
        }
    }

    fun changeNickname(
        userId: Long,
        nickname: String,
        register: Boolean,
    ) {
        if (register) {
            service.completeRegistration(userId, nickname, true, LocalDate.of(1990, 1, 1))
        } else {
            service.updateNickname(userId, nickname)
        }
    }

    withData(nameFn = { if (it) "두 계정의 동시 가입에서 닉네임은 한 계정만 선점한다" else "두 계정의 동시 닉네임 변경은 한 계정만 성공한다" }, true, false) { register ->
        val status = if (register) UserStatus.SUSPENDED else UserStatus.ACTIVE
        val first = users.save(User(nickname = "OriginalFirst", status = status))
        val second = users.save(User(nickname = "OriginalSecond", status = status))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val snapshotReady = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val holder = pool.submit {
            tx.executeWithoutResult {
                changeNickname(first.id, "ChosenNick", register)
                locked.countDown()
                check(release.await(15, TimeUnit.SECONDS))
            }
        }
        try {
            check(locked.await(10, TimeUnit.SECONDS))
            val contender = pool.submit<Result<Unit>> {
                runCatching {
                    tx.executeWithoutResult {
                        // Keep the availability snapshot older than the first commit to exercise the DB constraint.
                        service.isNicknameAvailable("chosennick") shouldBe true
                        snapshotReady.countDown()
                        changeNickname(second.id, "chosennick", register)
                    }
                }
            }
            check(snapshotReady.await(10, TimeUnit.SECONDS))
            shouldThrow<TimeoutException> { contender.get(500, TimeUnit.MILLISECONDS) }
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            val failure = contender.get(10, TimeUnit.SECONDS).exceptionOrNull()
            (failure as? NicknameDuplicatedException)?.errorCode shouldBe UserErrorCode.NICKNAME_DUPLICATED
            val saved = users.findByIdOrNull(second.id)!!
            saved.nickname shouldBe "OriginalSecond"
            saved.status shouldBe status
            saved.isAdult.shouldBeNull()
        } finally {
            release.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(15, TimeUnit.SECONDS))
        }
    }
})
