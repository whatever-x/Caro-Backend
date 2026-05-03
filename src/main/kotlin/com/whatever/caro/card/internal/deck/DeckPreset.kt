package com.whatever.caro.card.internal.deck

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "deck_presets")
class DeckPreset(
    @Column(name = "user_id", nullable = true, updatable = false)
    val userId: Long? = null,

    @Column(nullable = false, updatable = false)
    val name: String,

    @Column(name = "new_per_day", nullable = false, updatable = false)
    val newPerDay: Int = 20,

    @Column(name = "new_fair_interval", nullable = false, updatable = false)
    val newFairInterval: Int = 1,

    @Column(name = "new_easy_interval", nullable = false, updatable = false)
    val newEasyInterval: Int = 4,

    @Column(name = "new_initial_ease_factor", nullable = false, precision = 3, scale = 2, updatable = false)
    val newInitialEaseFactor: BigDecimal = BigDecimal("2.50"),

    @Column(name = "review_per_day", nullable = false, updatable = false)
    val reviewPerDay: Int = 40,

    @Column(name = "review_max_interval", nullable = false, updatable = false)
    val reviewMaxInterval: Int = 36500,

    @Column(name = "lapse_interval_multiplier", nullable = false, precision = 3, scale = 2, updatable = false)
    val lapseIntervalMultiplier: BigDecimal = BigDecimal("0.40"),

    @Column(name = "lapse_min_interval", nullable = false, updatable = false)
    val lapseMinInterval: Int = 1,

    @Column(name = "leech_threshold", nullable = false, updatable = false)
    val leechThreshold: Int = 8,

    @Column(name = "hard_badge_threshold", nullable = false, updatable = false)
    val hardBadgeThreshold: Int = 3,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
