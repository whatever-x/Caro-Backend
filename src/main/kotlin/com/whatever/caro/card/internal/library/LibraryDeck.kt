package com.whatever.caro.card.internal.library

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "library_decks")
class LibraryDeck(
    @Column(nullable = false, length = 50) var name: String,
    @Column(nullable = false, length = 500) var description: String,
    @Column(nullable = false) var published: Boolean = false,
    @Column(nullable = false) var sortOrder: Int = 0,
) : BaseTimeEntity() {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
