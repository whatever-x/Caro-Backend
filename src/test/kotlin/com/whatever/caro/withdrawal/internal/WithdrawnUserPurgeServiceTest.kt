package com.whatever.caro.withdrawal.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.card.internal.card.Card
import com.whatever.caro.card.internal.card.CardRepository
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckPreset
import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteType
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StreakType
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.streak.StreakState
import com.whatever.caro.study.internal.streak.StreakStateRepository
import com.whatever.caro.study.internal.streak.StudyDay
import com.whatever.caro.study.internal.streak.StudyDayRepository
import com.whatever.caro.study.internal.studysession.ReviewLog
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.internal.SocialAccount
import com.whatever.caro.user.internal.SocialAccountRepository
import com.whatever.caro.user.internal.User
import com.whatever.caro.user.internal.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 회원 탈퇴 데이터 파기 오케스트레이션 통합 테스트.
 * user/study/card 모듈의 실제 빈을 함께 띄워(ALL_DEPENDENCIES) 11개 테이블이 실제로 비워지는지,
 * 그리고 다른 유저 데이터/시스템 프리셋/공유 템플릿은 보존되는지 검증한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES, extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class WithdrawnUserPurgeServiceTest(
    private val purgeService: WithdrawnUserPurgeService,
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val noteRepository: NoteRepository,
    private val deckPresetRepository: DeckPresetRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val studySessionRepository: StudySessionRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val streakStateRepository: StreakStateRepository,
    private val studyDayRepository: StudyDayRepository,
) : DescribeSpec({

    afterEach {
        // FK 자식 → 부모 순으로 정리
        reviewLogRepository.deleteAllInBatch()
        cardRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
        studySessionRepository.deleteAllInBatch()
        studyDayRepository.deleteAllInBatch()
        streakStateRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
        deckRepository.deleteAllInBatch()
        deckPresetRepository.deleteAllInBatch()
        cardTemplateRepository.deleteAllInBatch()
        noteTypeRepository.deleteAllInBatch()
        socialAccountRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    data class Seed(
        val socialAccountId: Long,
        val noteId: Long,
        val presetId: Long,
        val deckId: Long,
        val cardId: Long,
        val cardLearningStateId: Long,
        val studySessionId: Long,
        val reviewLogId: Long,
        val streakStateId: Long,
        val studyDayId: Long,
    )

    fun seed(
        user: User,
        tag: String,
        cardTemplate: CardTemplate,
    ): Seed {
        val userId = user.id
        val social = socialAccountRepository.save(
            SocialAccount(user = user, provider = SocialProvider.GOOGLE, providerUserId = "google-$tag"),
        )
        val note = noteRepository.save(Note(userId = userId, fields = mapOf("front" to "a", "back" to "b")))
        val preset = deckPresetRepository.save(DeckPreset(userId = userId, name = "preset-$tag"))
        val deck = deckRepository.save(Deck(userId = userId, deckPreset = preset, name = "deck-$tag"))
        val card = cardRepository.save(Card(cardTemplate = cardTemplate, note = note, deck = deck, userId = userId))
        val cls = cardLearningStateRepository.save(
            CardLearningState(cardId = card.id, deckId = deck.id, userId = userId),
        )
        val session = studySessionRepository.save(
            StudySession(
                userId = userId,
                deckId = deck.id,
                studyType = StudyType.DAILY,
                startedAt = Instant.now(),
                timezone = ZoneId.of("UTC"),
                dayCutoffHour = 0,
                deckPresetIdSnapshot = preset.id,
            ),
        )
        val reviewLog = reviewLogRepository.save(
            ReviewLog(
                studySession = session,
                cardId = card.id,
                userId = userId,
                rating = Rating.FAIR,
                timeMs = 100,
                reviewType = ReviewType.NEW,
                previousIntervalDays = 0,
                previousEaseFactor = BigDecimal("2.50"),
                previousCardStatus = CardLearningStatus.NEW,
                intervalDays = 1,
                easeFactor = BigDecimal("2.50"),
            ),
        )
        val streak = streakStateRepository.save(
            StreakState(userId = userId, currentStreak = 1, lastRecordedDate = LocalDate.now()),
        )
        val studyDay = studyDayRepository.save(
            StudyDay(userId = userId, streakType = StreakType.DAILY_STUDY, studyDate = LocalDate.now()),
        )
        return Seed(
            socialAccountId = social.id,
            noteId = note.id,
            presetId = preset.id,
            deckId = deck.id,
            cardId = card.id,
            cardLearningStateId = cls.id,
            studySessionId = session.id,
            reviewLogId = reviewLog.id,
            streakStateId = streak.id,
            studyDayId = studyDay.id,
        )
    }

    describe("purge") {
        it("탈퇴 유저의 모든 데이터를 삭제하고, 다른 유저·시스템 프리셋·공유 템플릿은 보존한다") {
            // 공유 리소스(개인정보 아님) → 보존 대상
            val noteType = noteTypeRepository.save(NoteType(name = "basic"))
            val cardTemplate = cardTemplateRepository.save(
                CardTemplate(
                    noteType = noteType,
                    requiredFields = listOf("front", "back"),
                    template = mapOf("front" to "{{front}}"),
                    position = 0,
                ),
            )
            val systemPreset = deckPresetRepository.save(DeckPreset(userId = null, name = "system"))

            val withdrawnUser = userRepository.save(User(nickname = "withdrawn-user", status = UserStatus.ACTIVE))
            withdrawnUser.softDelete(Instant.now())
            userRepository.save(withdrawnUser)
            val activeUser = userRepository.save(User(nickname = "active-user", status = UserStatus.ACTIVE))

            val purged = seed(withdrawnUser, "w", cardTemplate)
            val kept = seed(activeUser, "a", cardTemplate)

            purgeService.purge(withdrawnUser.id)

            // 탈퇴 유저: 11개 테이블 모두 삭제
            socialAccountRepository.existsById(purged.socialAccountId).shouldBeFalse()
            noteRepository.existsById(purged.noteId).shouldBeFalse()
            deckPresetRepository.existsById(purged.presetId).shouldBeFalse()
            deckRepository.existsById(purged.deckId).shouldBeFalse()
            cardRepository.existsById(purged.cardId).shouldBeFalse()
            cardLearningStateRepository.existsById(purged.cardLearningStateId).shouldBeFalse()
            studySessionRepository.existsById(purged.studySessionId).shouldBeFalse()
            reviewLogRepository.existsById(purged.reviewLogId).shouldBeFalse()
            streakStateRepository.existsById(purged.streakStateId).shouldBeFalse()
            studyDayRepository.existsById(purged.studyDayId).shouldBeFalse()
            userRepository.existsById(withdrawnUser.id).shouldBeFalse()

            // 다른 유저: 모두 보존
            socialAccountRepository.existsById(kept.socialAccountId).shouldBeTrue()
            noteRepository.existsById(kept.noteId).shouldBeTrue()
            deckPresetRepository.existsById(kept.presetId).shouldBeTrue()
            deckRepository.existsById(kept.deckId).shouldBeTrue()
            cardRepository.existsById(kept.cardId).shouldBeTrue()
            cardLearningStateRepository.existsById(kept.cardLearningStateId).shouldBeTrue()
            studySessionRepository.existsById(kept.studySessionId).shouldBeTrue()
            reviewLogRepository.existsById(kept.reviewLogId).shouldBeTrue()
            streakStateRepository.existsById(kept.streakStateId).shouldBeTrue()
            studyDayRepository.existsById(kept.studyDayId).shouldBeTrue()
            userRepository.existsById(activeUser.id).shouldBeTrue()

            // 시스템 프리셋(user_id NULL)과 공유 템플릿: 보존
            deckPresetRepository.existsById(systemPreset.id).shouldBeTrue()
            cardTemplateRepository.existsById(cardTemplate.id).shouldBeTrue()
            noteTypeRepository.existsById(noteType.id).shouldBeTrue()
        }

        it("이미 파기된 유저를 다시 파기해도 예외 없이 멱등하게 동작한다") {
            val user = userRepository.save(User(nickname = "twice-user", status = UserStatus.ACTIVE))

            purgeService.purge(user.id)
            purgeService.purge(user.id)

            userRepository.existsById(user.id).shouldBeFalse()
        }
    }
})
