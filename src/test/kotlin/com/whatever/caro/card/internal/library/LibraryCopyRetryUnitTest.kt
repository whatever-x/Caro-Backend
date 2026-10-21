package com.whatever.caro.card.internal.library

import com.whatever.caro.common.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import java.sql.SQLException
import java.util.UUID

class LibraryCopyRetryUnitTest :
    DescribeSpec({
        val receipts = mockk<LibraryCopyReceiptRepository>()
        val transactions = mockk<PlatformTransactionManager>()
        val service = LibraryService(mockk(), mockk(), receipts, mockk(), mockk(), mockk(), mockk(), transactions)
        val key = UUID.randomUUID()
        val completed = LibraryCopyReceipt(1L, key.toString(), 10L).apply {
            ordinal = 1L
            deckId = 42L
            name = "복사된 덱"
            description = "설명"
            cardCount = 2
        }

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
            every { receipts.lockByKey(1L, key.toString()) } returns completed
        }
        afterEach { clearMocks(receipts, transactions) }

        it("ordinal UNIQUE 충돌은 rollback 후 새 transaction에서 재시도한다") {
            every { receipts.claim(1L, key.toString(), 10L) } throws duplicate("library_copy_receipts.uk_library_copy_ordinal") andThen 1
            service.copy(1L, 10L, key).deckId shouldBe 42L
            verify(exactly = 2) { transactions.getTransaction(any()) }
            verify(exactly = 1) { transactions.rollback(any()) }
            verify(exactly = 1) { transactions.commit(any()) }
        }

        it("DB 잠금 경합은 rollback 후 다시 시도한다") {
            every { receipts.claim(1L, key.toString(), 10L) } throws CannotAcquireLockException("deadlock") andThen 1
            service.copy(1L, 10L, key).deckId shouldBe 42L
            verify(exactly = 1) { transactions.rollback(any()) }
            verify(exactly = 1) { transactions.commit(any()) }
        }

        it("ordinal 충돌이 계속되면 세 번만 시도하고 마지막 오류를 반환한다") {
            val failure = duplicate("uk_library_copy_ordinal")
            every { receipts.claim(1L, key.toString(), 10L) } throws failure
            shouldThrow<DataIntegrityViolationException> { service.copy(1L, 10L, key) } shouldBe failure
            verify(exactly = 3) { receipts.claim(1L, key.toString(), 10L) }
            verify(exactly = 3) { transactions.rollback(any()) }
            verify(exactly = 0) { transactions.commit(any()) }
        }

        it("다른 UNIQUE 제약 오류는 재시도하지 않는다") {
            val failure = duplicate("uk_other_content")
            every { receipts.claim(1L, key.toString(), 10L) } throws failure
            shouldThrow<DataIntegrityViolationException> { service.copy(1L, 10L, key) } shouldBe failure
            verify(exactly = 1) { receipts.claim(1L, key.toString(), 10L) }
        }

        it("business 오류는 재시도하지 않는다") {
            every { receipts.claim(1L, key.toString(), 10L) } throws BusinessException(LibraryErrorCode.INVALID_CONTENT)
            shouldThrow<BusinessException> { service.copy(1L, 10L, key) }
            verify(exactly = 1) { receipts.claim(1L, key.toString(), 10L) }
        }
    })
