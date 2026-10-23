package com.whatever.caro.card.internal.library

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "library_cards")
class LibraryCard(
    @Column(nullable = false) val libraryDeckId: Long,
    @Column(nullable = false, columnDefinition = "TEXT") var front: String,
    @Column(nullable = false, columnDefinition = "TEXT") var back: String,
    @Column(nullable = false) var position: Int,
) : BaseTimeEntity() {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
