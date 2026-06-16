package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.study.internal.DeckCardCount
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface CardLearningStateRepository : JpaRepository<CardLearningState, Long> {
    fun findAllByUserIdAndCardIdInAndDeletedAtIsNull(
        userId: Long,
        cardIds: Collection<Long>,
    ): List<CardLearningState>

    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.nextReviewAt < :nextSessionStart
            and cls.status = CardLearningStatus.REVIEW
            and cls.deletedAt is null
    """,
    )
    fun countTodayReviewCards(
        userId: Long,
        deckId: Long,
        nextSessionStart: Instant,
    ): Int

    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and cls.deletedAt is null
    """,
    )
    fun countNewCards(
        userId: Long,
        deckId: Long,
    ): Int

    @Query(
        """
        select cls from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.REVIEW
            and cls.nextReviewAt < :nextSessionStart
            and (cls.lastReviewedAt is null or cls.lastReviewedAt < :sessionStart)
            and cls.deletedAt is null
        order by cls.nextReviewAt asc, cls.id asc
    """,
    )
    fun findAllReviewCard(
        userId: Long,
        deckId: Long,
        sessionStart: Instant,
        nextSessionStart: Instant,
        pageable: Pageable,
    ): List<CardLearningState>

    @Query(
        """
        select cls from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and (cls.lastReviewedAt is null or cls.lastReviewedAt < :sessionStart)
            and cls.deletedAt is null
        order by cls.id asc
    """,
    )
    fun findAllNewCard(
        userId: Long,
        deckId: Long,
        pageable: Pageable,
        sessionStart: Instant,
    ): List<CardLearningState>

    /**
     * 오늘 학습하지 않은, NEW 상태인 LearningState를 반환
     */
    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and (cls.lastReviewedAt is null or cls.lastReviewedAt < :sessionStart)
            and cls.deletedAt is null
        order by cls.id asc
        """,
    )
    fun countRemainingNewCards(
        userId: Long,
        deckId: Long,
        sessionStart: Instant,
    ): Int

    @Query(
        """
        select new com.whatever.caro.study.internal.DeckCardCount(cls.deckId, count(cls)) from CardLearningState cls
        where cls.deckId in :deckIds
            and cls.status = CardLearningStatus.NEW
            and cls.deletedAt is null
        group by cls.deckId
        """,
    )
    fun countNewCardsByDeckIds(
        deckIds: Collection<Long>,
    ): List<DeckCardCount>

    @Query(
        """
        select new com.whatever.caro.study.internal.DeckCardCount(cls.deckId, count(cls)) from CardLearningState cls
        where cls.deckId in :deckIds
            and cls.status = CardLearningStatus.REVIEW
            and cls.deletedAt is null
            and cls.nextReviewAt < :nextSessionStart
        group by cls.deckId
        """,
    )
    fun countReviewCardsByDeckIds(
        deckIds: Collection<Long>,
        nextSessionStart: Instant,
    ): List<DeckCardCount>
}
