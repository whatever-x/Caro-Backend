package com.whatever.caro.card.internal

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "deck_preset")
class DeckPreset(
    @Id
    @GeneratedValue(GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "user_id", nullable = true)
    val userId: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "new_per_day", nullable = false)
    var newPerDay: Int = 20,

    @Column(name = "new_fair_interval", nullable = false)
    var newFairInterval: Int = 1,

    @Column(name = "new_easy_interval", nullable = false)
    var newEasyInterval: Int = 4,

    @Column(name = "new_initial_ease_factor", nullable = false, precision = 3, scale = 2)
    var newInitialEaseFactor: BigDecimal = BigDecimal("2.50"),

    @Column("review_per_day", nullable = false)
    var reviewPerDay: Int = 40,

    @Column(name = "review_max_interval", nullable = false)
    var reviewMaxInterval: Int = 36500,

    @Column(name = "lapse_interval_multiplier", nullable = false, precision = 3, scale = 2)
    var lapseIntervalMultiplier: BigDecimal = BigDecimal("0.40"),

    @Column(name = "lapse_min_interval", nullable = false)
    var lapseMinInterval: Int = 1,

    @Column(name = "leech_threshold", nullable = false)
    var leechThreshold: Int = 8,
) : BaseTimeEntity()
