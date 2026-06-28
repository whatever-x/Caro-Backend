package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.LocalDate

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class CardLearningStateRepositoryTest(
    private val cardLearningStateRepository: CardLearningStateRepository,
) : DescribeSpec({

    val sessionDate: LocalDate = LocalDate.parse("2026-05-18")
    val deletedAt: Instant = Instant.parse("2026-05-18T19:00:00Z")

    afterEach {
        cardLearningStateRepository.deleteAllInBatch()
    }

    fun createCls(
        cardId: Long,
        deckId: Long = 1L,
        userId: Long = 1L,
        status: CardLearningStatus = CardLearningStatus.REVIEW,
        nextReviewDate: LocalDate? = null,
    ): CardLearningState =
        cardLearningStateRepository.save(
            CardLearningState(
                cardId = cardId,
                deckId = deckId,
                userId = userId,
                status = status,
                nextReviewDate = nextReviewDate,
            ),
        )

    describe("countTodayReviewCards") {
        it("다음 복습일이 sessionDate와 같은(경계 포함) 카드는 포함된다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 1
        }

        it("다음 복습일이 sessionDate 다음날인 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewDate = sessionDate.plusDays(1),
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("다음 리뷰일이 없는 카드는 제외된다") {
            // status가 REVIEW이며, nextReviewDate이 null인 카드는 존재할 수 없지만 테스트를 위해 임의 생성
            val cls = createCls(
                cardId = 1L,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("요청 userId와 다른 userId의 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                userId = 1L,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = 2L, // other user
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("요청 deckId와 다른 deckId의 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                deckId = 1L,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = 2L, // other deck
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("매칭 조건을 만족하는 여러 카드는 합산된 카운트로 반환된다") {
            createCls(
                cardId = 1L,
                nextReviewDate = sessionDate,
            )
            createCls(
                cardId = 2L,
                nextReviewDate = sessionDate,
            )
            val cls = createCls(
                cardId = 3L,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 3
        }

        it("학습 대상 카드가 없을 경우 0을 반환한다") {
            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = 1L,
                deckId = 1L,
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("status=SUSPENDED 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.SUSPENDED,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 0
        }

        it("status=NEW 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                today = sessionDate,
            )

            count shouldBe 0
        }
    }

    describe("findAllByUserIdAndCardIdIn") {
        it("요청한 cardIds 전부가 존재할 때 모든 매칭 카드를 반환한다") {
            val clsList = listOf(
                createCls(cardId = 1L, nextReviewDate = sessionDate),
                createCls(cardId = 2L, nextReviewDate = sessionDate),
                createCls(cardId = 3L, nextReviewDate = sessionDate),
            )
            val cardIds = clsList.map { it.cardId }

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
                userId = clsList.first().userId,
                cardIds = cardIds,
            )

            result.map { it.cardId }.shouldContainExactlyInAnyOrder(cardIds)
        }

        it("요청 userId와 다른 userId의 카드는 결과에서 제외된다") {
            val cls = createCls(
                cardId = 1L,
                userId = 2L,
                nextReviewDate = sessionDate,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
                userId = 1L,
                cardIds = listOf(cls.cardId),
            )

            result.size shouldBe 0
        }

        it("빈 cardIds 컬렉션은 빈 결과를 반환한다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewDate = sessionDate,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
                userId = cls.userId,
                cardIds = emptyList(),
            )

            result.size shouldBe 0
        }

        it("요청한 cardIds 중 존재하는 카드만 부분 매칭으로 반환된다 (derived query IN 절 가드)") {
            val cls = createCls(
                cardId = 1L,
                nextReviewDate = sessionDate,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
                userId = cls.userId,
                cardIds = listOf(cls.cardId, 2L),
            )

            result.map { it.cardId }.shouldContainExactlyInAnyOrder(listOf(cls.cardId))
        }
    }

    describe("countNewCards") {
        it("status=NEW 카드는 포함된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 1
        }

        it("status=REVIEW 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.REVIEW,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 0
        }

        it("status=SUSPENDED 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.SUSPENDED,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 0
        }

        it("요청 userId와 다른 userId의 NEW 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                userId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = 2L, // other user
                deckId = cls.deckId,
            )

            count shouldBe 0
        }

        it("요청 deckId와 다른 deckId의 NEW 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                deckId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = 2L, // other deck
            )

            count shouldBe 0
        }

        it("매칭 조건을 만족하는 여러 NEW 카드는 합산해 반환된다") {
            createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )
            createCls(
                cardId = 2L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )
            val cls = createCls(
                cardId = 3L,
                status = CardLearningStatus.NEW,
                nextReviewDate = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 3
        }

        it("status=NEW 카드는 nextReviewDate 값과 무관하게 카운트에 포함된다") {
            // 정상적인 데이터라면 NEW 카드는 nextReviewDate가 존재하지 않음, 그러나 존재하더라도 무시
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewDate = sessionDate,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 1
        }
    }

    // TODO userId 필터 추가 시 격리 케이스 추가
    describe("countNewCardsByDeckIds") {
        it("덱별 NEW 카드 수가 deckId 기준으로 집계된다") {
            repeat(3) { i ->
                createCls(cardId = (i + 1).toLong(), deckId = 1L, status = CardLearningStatus.NEW)
            }
            repeat(5) { i ->
                createCls(cardId = (10 + i + 1).toLong(), deckId = 2L, status = CardLearningStatus.NEW)
            }

            val result = cardLearningStateRepository.countNewCardsByDeckIds(
                deckIds = setOf(1L, 2L),
            )

            result.associate { it.deckId to it.count } shouldBe mapOf(1L to 3L, 2L to 5L)
        }

        it("NEW 카드가 없는 덱은 결과에 행 자체가 없다") {
            createCls(cardId = 1L, deckId = 1L, status = CardLearningStatus.NEW)

            val result = cardLearningStateRepository.countNewCardsByDeckIds(
                deckIds = setOf(1L, 2L),
            )

            result.map { it.deckId } shouldBe listOf(1L)
        }

        it("REVIEW 상태 카드와 soft delete된 NEW 카드는 집계되지 않는다") {
            createCls(cardId = 1L, deckId = 1L, status = CardLearningStatus.NEW)
            createCls(cardId = 2L, deckId = 1L, status = CardLearningStatus.REVIEW, nextReviewDate = sessionDate)
            val deleted = createCls(cardId = 3L, deckId = 1L, status = CardLearningStatus.NEW)
            deleted.softDelete(deletedAt = deletedAt)
            cardLearningStateRepository.save(deleted)

            val result = cardLearningStateRepository.countNewCardsByDeckIds(
                deckIds = setOf(1L),
            )

            result.associate { it.deckId to it.count } shouldBe mapOf(1L to 1L)
        }
    }

    // TODO userId 필터 추가 시 격리 케이스 추가
    describe("countReviewCardsByDeckIds") {
        it("nextReviewDate가 sessionDate인 카드는 포함되고, 다음날인 카드는 제외된다") {
            createCls(cardId = 1L, deckId = 1L, nextReviewDate = sessionDate)
            createCls(cardId = 2L, deckId = 2L, nextReviewDate = sessionDate.plusDays(1))

            val result = cardLearningStateRepository.countReviewCardsByDeckIds(
                deckIds = setOf(1L, 2L),
                today = sessionDate,
            )

            result.associate { it.deckId to it.count } shouldBe mapOf(1L to 1L)
        }

        it("NEW 상태 카드와 soft delete된 REVIEW 카드는 집계되지 않는다") {
            createCls(cardId = 1L, deckId = 1L, nextReviewDate = sessionDate)
            createCls(cardId = 2L, deckId = 1L, status = CardLearningStatus.NEW, nextReviewDate = sessionDate)
            val deleted = createCls(cardId = 3L, deckId = 1L, nextReviewDate = sessionDate)
            deleted.softDelete(deletedAt = deletedAt)
            cardLearningStateRepository.save(deleted)

            val result = cardLearningStateRepository.countReviewCardsByDeckIds(
                deckIds = setOf(1L),
                today = sessionDate,
            )

            result.associate { it.deckId to it.count } shouldBe mapOf(1L to 1L)
        }
    }
})
