package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface DeckPresetRepository : JpaRepository<DeckPreset, Long> {

    /**
     * 유저 소유 프리셋만 삭제한다. user_id 가 NULL 인 시스템 프리셋은 :userId 와 매칭되지 않아 보존된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeckPreset dp where dp.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
