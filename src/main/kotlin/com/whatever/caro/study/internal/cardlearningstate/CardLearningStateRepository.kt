package com.whatever.caro.study.internal.cardlearningstate

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
        select count(*) from CardLearningState cls
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
        select count(*) from CardLearningState cls
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
}
