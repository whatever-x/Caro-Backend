package com.whatever.caro.card.internal

import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.common.entity.SoftDeletableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "cards")
class Card(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_template_id", nullable = false)
    val cardTemplate: CardTemplate,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    val note: Note,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    val deck: Deck,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var position: Int = 0, // TODO position 방식에 대해서는 추후 논의
) : SoftDeletableEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
