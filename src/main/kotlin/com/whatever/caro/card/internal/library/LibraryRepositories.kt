package com.whatever.caro.card.internal.library

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface LibraryDeckRepository : JpaRepository<LibraryDeck, Long> {
    fun findByPublishedTrueOrderBySortOrderAscIdAsc(): List<LibraryDeck>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from LibraryDeck d where d.id = :id")
    fun lockById(
        id: Long,
    ): LibraryDeck?
}

interface LibraryCardRepository : JpaRepository<LibraryCard, Long> {
    fun findByLibraryDeckIdOrderByPositionAscIdAsc(
        libraryDeckId: Long,
    ): List<LibraryCard>

    @Query(
        "select c.libraryDeckId as libraryDeckId, count(c) as cardCount from LibraryCard c where c.libraryDeckId in :ids group by c.libraryDeckId",
    )
    fun counts(
        ids: List<Long>,
    ): List<LibraryCardCount>
}

interface LibraryCardCount {
    val libraryDeckId: Long
    val cardCount: Long
}

interface LibraryCopyReceiptRepository : JpaRepository<LibraryCopyReceipt, Long> {
    // The unique user/key claim and row lock share the copy transaction, including rollback.
    @Modifying
    @Query(
        value = """INSERT INTO library_copy_receipts
        (user_id, request_key, library_deck_id, created_at, updated_at)
        VALUES (:userId, :requestKey, :libraryDeckId, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE id = id""",
        nativeQuery = true,
    )
    fun claim(
        userId: Long,
        requestKey: String,
        libraryDeckId: Long,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LibraryCopyReceipt r where r.userId = :userId and r.requestKey = :requestKey")
    fun lockByKey(
        userId: Long,
        requestKey: String,
    ): LibraryCopyReceipt?

    fun countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(
        userId: Long,
        libraryDeckId: Long,
    ): Long

    @Modifying
    @Query("delete from LibraryCopyReceipt r where r.userId = :userId")
    fun deleteAllByUserId(
        userId: Long,
    )
}
