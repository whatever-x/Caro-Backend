package com.whatever.caro.card.internal

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "card_template")
class CardTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_type_id", nullable = false)
    val noteType: NoteType,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields", nullable = false, columnDefinition = "JSON")
    var requiredFields: List<String>,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    var template: Map<String, String>,

    @Column(nullable = false)
    var position: Int,
) : BaseTimeEntity()
