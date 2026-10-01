package com.whatever.caro.card.internal.library

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "library_copy_receipts")
class LibraryCopyReceipt(
    @Column(nullable = false) val userId: Long,
    @Column(nullable = false, length = 36) val requestKey: String,
    @Column(nullable = false) val libraryDeckId: Long,
) : BaseTimeEntity() {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
    var ordinal: Long? = null
    var deckId: Long? = null

    @Column(length = 50) var name: String? = null

    @Column(length = 500) var description: String? = null
    var cardCount: Int? = null

    fun result(): LibraryCopyResponse? =
        deckId?.let {
            LibraryCopyResponse(it, requireNotNull(name), requireNotNull(description), requireNotNull(cardCount))
        }

    fun complete(
        number: Long,
        result: LibraryCopyResponse,
    ) {
        ordinal = number
        deckId = result.deckId
        name = result.name
        description = result.description
        cardCount = result.cardCount
    }
}
