package com.whatever.caro.user.internal

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.AlreadyCompletedException
import com.whatever.caro.user.exception.UserNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CaroModuleTest(extraIncludes = ["common"])
class UserMutationServiceTest(
    private val service: UserService,
    private val users: UserRepository,
    private val socialAccounts: SocialAccountRepository,
    transactionManager: PlatformTransactionManager,
) : DescribeSpec({
    val tx = TransactionTemplate(transactionManager)

    afterTest {
        socialAccounts.deleteAllInBatch()
        users.deleteAllInBatch()
    }

    it("같은 계정의 두 번째 가입은 첫 가입 커밋을 기다린 뒤 거부한다") {
        val user = users.save(User(nickname = "before"))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val holder = pool.submit {
            tx.executeWithoutResult {
                service.completeRegistration(user.id, "AdultNick", true, LocalDate.of(1990, 1, 1))
                users.flush()
                locked.countDown()
                check(release.await(15, TimeUnit.SECONDS))
            }
        }
        try {
            check(locked.await(10, TimeUnit.SECONDS))
            val contender = pool.submit<Result<Unit>> {
                started.countDown()
                runCatching {
                    service.completeRegistration(user.id, "MinorNick", true, LocalDate.of(2020, 1, 1))
                    Unit
                }
            }
            check(started.await(10, TimeUnit.SECONDS))
            shouldThrow<TimeoutException> { contender.get(500, TimeUnit.MILLISECONDS) }
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            (contender.get(10, TimeUnit.SECONDS).exceptionOrNull() is AlreadyCompletedException) shouldBe true
            val saved = users.findByIdOrNull(user.id)!!
            saved.status shouldBe UserStatus.ACTIVE
            saved.nickname shouldBe "AdultNick"
            saved.isAdult shouldBe true
        } finally {
            release.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    withData(nameFn = { if (it) "탈퇴 뒤 닉네임 변경은 성인 여부와 삭제 상태를 복원하지 않는다" else "닉네임 변경 뒤 탈퇴는 최신 값을 보존하고 성인 여부를 지운다" }, true, false) { deleteFirst ->
        val user = users.save(User(nickname = "before", status = UserStatus.ACTIVE, isTermsAgreed = true, isAdult = true))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val holder = pool.submit {
            tx.executeWithoutResult {
                if (deleteFirst) service.deleteMe(user.id) else service.updateNickname(user.id, "UpdatedNick")
                users.flush()
                locked.countDown()
                check(release.await(15, TimeUnit.SECONDS))
            }
        }
        try {
            check(locked.await(10, TimeUnit.SECONDS))
            val contender = pool.submit<Result<Unit>> {
                started.countDown()
                runCatching {
                    if (deleteFirst) service.updateNickname(user.id, "UpdatedNick") else service.deleteMe(user.id)
                    Unit
                }
            }
            check(started.await(10, TimeUnit.SECONDS))
            shouldThrow<TimeoutException> { contender.get(500, TimeUnit.MILLISECONDS) }
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            val result = contender.get(10, TimeUnit.SECONDS)
            if (deleteFirst) {
                (result.exceptionOrNull() is UserNotFoundException) shouldBe true
            } else {
                result.isSuccess shouldBe true
            }
            val saved = users.findByIdOrNull(user.id)!!
            saved.isDeleted shouldBe true
            saved.isAdult.shouldBeNull()
            saved.nickname shouldBe if (deleteFirst) "before" else "UpdatedNick"
        } finally {
            release.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(15, TimeUnit.SECONDS))
        }
    }
})
