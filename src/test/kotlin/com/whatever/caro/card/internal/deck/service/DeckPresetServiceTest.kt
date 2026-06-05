package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckPreset
import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.deck.exception.DeckPresetNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class DeckPresetServiceTest(
    private val deckPresetService: DeckPresetService,
    private val deckRepository: DeckRepository,
    private val deckPresetRepository: DeckPresetRepository,
) : DescribeSpec({

    afterTest {
        deckRepository.deleteAllInBatch()
        deckPresetRepository.deleteAllInBatch()
    }

    fun savePreset(
        userId: Long? = 1L,
        name: String = "기본 프리셋",
        newPerDay: Int = 30,
    ): DeckPreset = deckPresetRepository.save(DeckPreset(userId = userId, name = name, newPerDay = newPerDay))

    fun saveDeck(
        userId: Long,
        preset: DeckPreset? = null,
        name: String = "테스트 덱",
    ): Deck = deckRepository.save(Deck(userId = userId, deckPreset = preset, name = name))

    describe("getLatestDeckPresetByUser") {
        it("덱에 연결된 프리셋을 DTO로 반환한다") {
            val userId = 1L
            val preset = savePreset(userId = userId, name = "내 프리셋", newPerDay = 25)
            val deck = saveDeck(userId = userId, preset = preset)

            val result = deckPresetService.getLatestDeckPresetByUser(deck.id, userId)

            result.id shouldBe preset.id
            result.userId shouldBe preset.userId
        }

        it("존재하지 않는 덱이면 DeckNotFoundException을 던진다") {
            shouldThrow<DeckNotFoundException> {
                deckPresetService.getLatestDeckPresetByUser(999L, 1L)
            }
        }

        it("soft delete된 덱이면 DeckNotFoundException을 던진다") {
            val userId = 1L
            val preset = savePreset(userId = userId)
            val deck = saveDeck(userId = userId, preset = preset)
            deck.softDelete(deletedAt = Instant.now())
            deckRepository.save(deck)

            shouldThrow<DeckNotFoundException> {
                deckPresetService.getLatestDeckPresetByUser(deck.id, userId)
            }
        }

        it("다른 유저의 덱이면 DeckForbiddenException을 던진다") {
            val preset = savePreset(userId = 2L)
            val deck = saveDeck(userId = 2L, preset = preset)

            shouldThrow<DeckForbiddenException> {
                deckPresetService.getLatestDeckPresetByUser(deck.id, 1L)
            }
        }

        it("프리셋이 할당되지 않은 덱이면 DeckPresetNotFoundException을 던진다") {
            val userId = 1L
            val deck = saveDeck(userId = userId, preset = null)

            shouldThrow<DeckPresetNotFoundException> {
                deckPresetService.getLatestDeckPresetByUser(deck.id, userId)
            }
        }
    }

    describe("getDeckPresetById") {
        it("프리셋 id로 DTO를 반환한다") {
            val preset = savePreset(name = "스냅샷 프리셋", newPerDay = 15)

            val result = deckPresetService.getDeckPresetById(preset.id)

            result.id shouldBe preset.id
        }

        it("존재하지 않는 프리셋이면 DeckPresetNotFoundException을 던진다") {
            shouldThrow<DeckPresetNotFoundException> {
                deckPresetService.getDeckPresetById(999L)
            }
        }
    }
})
