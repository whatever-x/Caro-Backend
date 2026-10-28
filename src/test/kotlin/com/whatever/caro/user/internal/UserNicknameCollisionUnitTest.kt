package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.exception.NicknameDuplicatedException
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import java.sql.SQLException
import java.time.Clock

class UserNicknameCollisionUnitTest :
    DescribeSpec({
        val users = mockk<UserRepository>()
        val accounts = mockk<SocialAccountRepository>()
        val transactions = mockk<PlatformTransactionManager>()
        val service = UserService(Clock.systemUTC(), mockk<EmailHasher>(relaxed = true), users, accounts, transactions)
        val provider = SocialProvider.GOOGLE
        val providerId = "google-123"

        fun duplicate(
            constraint: String,
        ) = DataIntegrityViolationException(
            "duplicate",
            ConstraintViolationException("duplicate", SQLException("duplicate", "23000", 1062), constraint),
        )

        beforeEach {
            every { transactions.getTransaction(any()) } answers {
                firstArg<TransactionDefinition>().propagationBehavior shouldBe TransactionDefinition.PROPAGATION_REQUIRES_NEW
                SimpleTransactionStatus()
            }
            justRun { transactions.commit(any()) }
            justRun { transactions.rollback(any()) }
            every { users.save(any()) } answers { firstArg() }
            every { accounts.save(any()) } answers { firstArg() }
            justRun { users.flush() }
        }
        afterTest { clearMocks(users, accounts, transactions) }

        it("임시 닉네임 충돌은 rollback 후 새 트랜잭션에서 재시도한다") {
            every { users.flush() } throws duplicate("users.uk_users_active_nickname") andThen Unit

            service.createSocialUser(provider, providerId, null).nickname.startsWith("temp_") shouldBe true

            verifyOrder {
                transactions.getTransaction(any())
                users.flush()
                transactions.rollback(any())
                transactions.getTransaction(any())
                users.flush()
                transactions.commit(any())
            }
            verify(exactly = 2) { transactions.getTransaction(any()) }
            verify(exactly = 1) { transactions.rollback(any()) }
            verify(exactly = 1) { transactions.commit(any()) }
        }

        it("임시 닉네임 충돌이 계속되면 세 번 시도하고 마지막 오류를 전파한다") {
            val failure = duplicate("uk_users_active_nickname")
            every { users.flush() } throws failure

            shouldThrow<DataIntegrityViolationException> { service.createSocialUser(provider, providerId, null) } shouldBe failure
            verify(exactly = 3) { transactions.getTransaction(any()) }
            verify(exactly = 3) { users.flush() }
            verify(exactly = 3) { transactions.rollback(any()) }
            verify(exactly = 0) { transactions.commit(any()) }
        }

        it("provider 충돌은 rollback 후 새 트랜잭션에서 기존 계정을 조회한다") {
            val existing = User(nickname = "existing")
            every { users.flush() } throws duplicate("social_accounts.uk_provider_user")
            every { accounts.findByProviderAndProviderUserId(provider, providerId) } returns
                SocialAccount(existing, provider, providerId)

            service.createSocialUser(provider, providerId, null).nickname shouldBe "existing"

            verifyOrder {
                transactions.getTransaction(any())
                users.flush()
                transactions.rollback(any())
                transactions.getTransaction(any())
                accounts.findByProviderAndProviderUserId(provider, providerId)
                transactions.commit(any())
            }
            verify(exactly = 2) { transactions.getTransaction(any()) }
            verify(exactly = 1) { transactions.rollback(any()) }
            verify(exactly = 1) { transactions.commit(any()) }
            verify(exactly = 1) { accounts.findByProviderAndProviderUserId(provider, providerId) }
        }

        it("다른 제약 오류는 재시도하지 않고 전파한다") {
            val failure = duplicate("uk_other")
            every { users.flush() } throws failure

            shouldThrow<DataIntegrityViolationException> { service.createSocialUser(provider, providerId, null) } shouldBe failure
            verify(exactly = 1) { transactions.getTransaction(any()) }
            verify(exactly = 1) { transactions.rollback(any()) }
            verify(exactly = 0) { transactions.commit(any()) }
            verify(exactly = 0) { accounts.findByProviderAndProviderUserId(any(), any()) }
        }

        describe("닉네임 변경 flush") {
            beforeEach {
                every { users.findByIdForUpdate(1L) } returns User(nickname = "before")
                every { users.existsByNicknameAndDeletedAtIsNull("after") } returns false
            }

            it("가입 시 닉네임 제약 충돌은 U003으로 변환한다") {
                every { users.flush() } throws duplicate("users.uk_users_active_nickname")
                val failure = shouldThrow<NicknameDuplicatedException> {
                    service.completeRegistration(1L, "after", true)
                }
                failure.errorCode.code shouldBe "U003"
            }

            it("닉네임 변경 시 닉네임 제약 충돌은 U003으로 변환한다") {
                every { users.flush() } throws duplicate("uk_users_active_nickname")
                val failure = shouldThrow<NicknameDuplicatedException> { service.updateNickname(1L, "after") }
                failure.errorCode.code shouldBe "U003"
            }

            it("가입 시 다른 제약 오류는 그대로 전파한다") {
                val failure = duplicate("uk_other")
                every { users.flush() } throws failure
                shouldThrow<DataIntegrityViolationException> { service.completeRegistration(1L, "after", true) } shouldBe failure
            }

            it("닉네임 변경 시 다른 제약 오류는 그대로 전파한다") {
                val failure = duplicate("uk_other")
                every { users.flush() } throws failure
                shouldThrow<DataIntegrityViolationException> { service.updateNickname(1L, "after") } shouldBe failure
            }
        }
    })
