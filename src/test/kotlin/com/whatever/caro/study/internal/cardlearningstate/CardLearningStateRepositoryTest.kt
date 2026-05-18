package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.CardLearningStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class CardLearningStateRepositoryTest(
    private val cardLearningStateRepository: CardLearningStateRepository,
) : DescribeSpec({

    val kstZoneId = ZoneId.of("Asia/Seoul")

    // 기준: nextSessionStart = KST 2026-05-19 04:00:00 = UTC 2026-05-18 19:00:00
    val nextSessionStart = LocalDateTime.parse("2026-05-19T04:00:00").atZone(kstZoneId).toInstant()
    val beforeCutoff = LocalDateTime.parse("2026-05-19T03:59:59").atZone(kstZoneId).toInstant()
    val afterCutoff = LocalDateTime.parse("2026-05-19T04:00:01").atZone(kstZoneId).toInstant()

    afterEach {
        cardLearningStateRepository.deleteAllInBatch()
    }

    fun createCls(
        cardId: Long,
        deckId: Long = 1L,
        userId: Long = 1L,
        status: CardLearningStatus = CardLearningStatus.REVIEW,
        nextReviewAt: Instant? = null,
    ): CardLearningState =
        cardLearningStateRepository.save(
            CardLearningState(
                cardId = cardId,
                deckId = deckId,
                userId = userId,
                status = status,
                nextReviewAt = nextReviewAt,
            ),
        )

    describe("countTodayReviewCards") {
        it("다음 리뷰일이 오늘 세션이 끝나기 직전(nextSessionStart -1초)인 카드는 포함된다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewAt = nextSessionStart.minusSeconds(1),
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 1
        }

        it("다음 리뷰일이 오늘 세션에 포함되지 않은(nextSessionStart와 동일한) 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewAt = nextSessionStart,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("다음 리뷰일이 없는 카드는 제외된다") {
            // status가 REVIEW이며, nextReviewAt이 null인 카드는 존재할 수 없지만 테스트를 위해 임의 생성
            val cls = createCls(
                cardId = 1L,
                nextReviewAt = null,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("요청 userId와 다른 userId의 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                userId = 1L,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = 2L, // other user
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("요청 deckId와 다른 deckId의 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                deckId = 1L,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = 2L, // other deck
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("매칭 조건을 만족하는 여러 카드는 합산된 카운트로 반환된다") {
            createCls(
                cardId = 1L,
                nextReviewAt = beforeCutoff,
            )
            createCls(
                cardId = 2L,
                nextReviewAt = beforeCutoff,
            )
            val cls = createCls(
                cardId = 3L,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 3
        }

        it("학습 대상 카드가 없을 경우 0을 반환한다") {
            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = 1L,
                deckId = 1L,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("status=SUSPENDED 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.SUSPENDED,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }

        it("status=NEW 카드는 제외된다") {
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countTodayReviewCards(
                userId = cls.userId,
                deckId = cls.deckId,
                nextSessionStart = nextSessionStart,
            )

            count shouldBe 0
        }
    }

    describe("findAllByUserIdAndCardIdIn") {
        it("요청한 cardIds 전부가 존재할 때 모든 매칭 카드를 반환한다") {
            val clsList = listOf(
                createCls(cardId = 1L, nextReviewAt = beforeCutoff),
                createCls(cardId = 2L, nextReviewAt = beforeCutoff),
                createCls(cardId = 3L, nextReviewAt = beforeCutoff),
            )
            val cardIds = clsList.map { it.cardId }

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
                userId = clsList.first().userId,
                cardIds = cardIds,
            )

            result.map { it.cardId }.shouldContainExactlyInAnyOrder(cardIds)
        }

        it("요청 userId와 다른 userId의 카드는 결과에서 제외된다") {
            val cls = createCls(
                cardId = 1L,
                userId = 2L,
                nextReviewAt = beforeCutoff,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
                userId = 1L,
                cardIds = listOf(cls.cardId),
            )

            result.size shouldBe 0
        }

        it("빈 cardIds 컬렉션은 빈 결과를 반환한다") {
            val cls = createCls(
                cardId = 1L,
                nextReviewAt = beforeCutoff,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
                userId = cls.userId,
                cardIds = emptyList(),
            )

            result.size shouldBe 0
        }

        it("요청한 cardIds 중 존재하는 카드만 부분 매칭으로 반환된다 (derived query IN 절 가드)") {
            val cls = createCls(
                cardId = 1L,
                nextReviewAt = beforeCutoff,
            )

            val result = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
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
                nextReviewAt = null,
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
                nextReviewAt = beforeCutoff,
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
                nextReviewAt = null,
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
                nextReviewAt = null,
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
                nextReviewAt = null,
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
                nextReviewAt = null,
            )
            createCls(
                cardId = 2L,
                status = CardLearningStatus.NEW,
                nextReviewAt = null,
            )
            val cls = createCls(
                cardId = 3L,
                status = CardLearningStatus.NEW,
                nextReviewAt = null,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 3
        }

        it("status=NEW 카드는 nextReviewAt 값과 무관하게 카운트에 포함된다") {
            // 정상적인 데이터라면 NEW 카드는 nextReviewAt이 존재하지 않음, 그러나 존재하더라도 무시
            val cls = createCls(
                cardId = 1L,
                status = CardLearningStatus.NEW,
                nextReviewAt = beforeCutoff,
            )

            val count = cardLearningStateRepository.countNewCards(
                userId = cls.userId,
                deckId = cls.deckId,
            )

            count shouldBe 1
        }
    }
})
