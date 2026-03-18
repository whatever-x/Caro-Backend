package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository

interface DeckPresetRepository : JpaRepository<DeckPreset, Long>
