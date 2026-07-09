package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.card.internal.card.dto.create.CardType
import com.whatever.caro.card.internal.card.dto.create.CreateCardItem
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsRequest
import com.whatever.caro.card.internal.card.dto.create.CreateCardsResponseDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardResponseDto
import com.whatever.caro.card.internal.card.dto.read.CardResponseDto
import com.whatever.caro.card.internal.card.dto.update.UpdateCardDto
import com.whatever.caro.card.internal.card.dto.update.UpdateCardRequest
import com.whatever.caro.card.internal.card.dto.update.UpdateCardResponseDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZoneId

class CardControllerUnitTest :
    DescribeSpec({

        val cardService = mockk<CardService>()
        val controller = CardController(cardService)

        beforeEach {
            val authUser = AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE")
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(authUser, null, emptyList())
        }

        afterEach {
            SecurityContextHolder.clearContext()
        }

        describe("createCards") {
            it("CardType.BASIC 을 noteTypeId 1L 로 매핑하고 200 과 응답을 반환한다") {
                val deckId = 10L
                val request = CreateCardsRequest(
                    items = listOf(
                        CreateCardItem(
                            cardType = CardType.BASIC,
                            fields = mapOf("front" to "apple", "back" to "사과"),
                        ),
                    ),
                )
                every { cardService.createCards(1L, any()) } returns CreateCardsResponseDto(
                    items = listOf(CardResponseDto(cardId = 100L, fields = mapOf("front" to "apple"))),
                )

                val response = controller.createCards(deckId, request)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.items[0].cardId shouldBe 100L
                response.body!!.data!!.items[0].fields shouldBe mapOf("front" to "apple")
                verify {
                    cardService.createCards(
                        1L,
                        CreateCardsDto(
                            deckId = deckId,
                            items = listOf(
                                CreateCardItemDto(
                                    noteTypeId = CardType.BASIC.noteTypeId,
                                    fields = mapOf("front" to "apple", "back" to "사과"),
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        describe("getCardsByDeck") {
            it("200 과 카드 목록을 반환한다") {
                every { cardService.getCardsByDeck(1L, 10L) } returns listOf(
                    CardResponseDto(cardId = 100L, fields = mapOf("front" to "a")),
                    CardResponseDto(cardId = 101L, fields = mapOf("front" to "b")),
                )

                val response = controller.getCardsByDeck(10L)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.size shouldBe 2
                response.body!!.data!![0].cardId shouldBe 100L
                response.body!!.data!![1].cardId shouldBe 101L
            }
        }

        describe("getCard") {
            it("200 과 카드 정보를 반환한다") {
                every { cardService.getCard(1L, 100L) } returns CardResponseDto(
                    cardId = 100L,
                    fields = mapOf("front" to "apple"),
                )

                val response = controller.getCard(100L)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.cardId shouldBe 100L
                response.body!!.data!!.fields shouldBe mapOf("front" to "apple")
            }
        }

        describe("updateCard") {
            it("dto 로 변환된 요청을 서비스에 위임하고 200 을 반환한다") {
                val request = UpdateCardRequest(fields = mapOf("front" to "banana"))
                every { cardService.updateCard(1L, any()) } returns UpdateCardResponseDto(
                    cardId = 100L,
                    fields = mapOf("front" to "banana", "back" to "사과"),
                )

                val response = controller.updateCard(100L, request)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.cardId shouldBe 100L
                response.body!!.data!!.fields shouldBe mapOf("front" to "banana", "back" to "사과")
                verify {
                    cardService.updateCard(
                        1L,
                        UpdateCardDto(cardId = 100L, fields = mapOf("front" to "banana")),
                    )
                }
            }
        }

        describe("deleteCard") {
            val clientTimezone = ZoneId.of("Asia/Seoul")

            it("DeleteCardDto로 서비스에 위임하고 200과 삭제된 카드 id를 반환한다") {
                every {
                    cardService.deleteCard(1L, clientTimezone, DeleteCardDto(cardId = 100L))
                } returns DeleteCardResponseDto(cardId = 100L)

                val response = controller.deleteCard(100L, clientTimezone)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.cardId shouldBe 100L
                verify { cardService.deleteCard(1L, clientTimezone, DeleteCardDto(cardId = 100L)) }
            }
        }
    })
