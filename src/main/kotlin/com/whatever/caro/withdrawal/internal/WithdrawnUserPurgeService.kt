package com.whatever.caro.withdrawal.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.study.StudyApi
import com.whatever.caro.user.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WithdrawnUserPurgeService(
    private val studyApi: StudyApi,
    private val cardApi: CardApi,
    private val deckApi: DeckApi,
    private val userApi: UserApi,
) {
    /**
     * 탈퇴 유저 1명의 모든 개인정보를 하드 삭제한다.
     *
     * 삭제 순서는 FK 자식 → 부모: study → card(cards/notes) → deck(decks/deck_presets) → user.
     * userId 단위 단일 트랜잭션이라 중간 실패 시 해당 유저만 롤백되고, 이미 지운 행은 다시 지워도
     * 0건 처리되어 재실행에 안전(멱등)하다.
     */
    @Transactional
    fun purge(
        userId: Long,
    ) {
        studyApi.deleteAllByUserId(userId)
        cardApi.deleteAllByUserId(userId)
        deckApi.deleteAllByUserId(userId)
        userApi.hardDeleteUser(userId)
    }
}
