package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponseDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckResponseDto
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckRequest
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckResponseDto
import com.whatever.caro.card.internal.deck.service.DeckService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class DeckControllerUnitTest :
    DescribeSpec({

        val deckService = mockk<DeckService>()
        val controller = DeckController(deckService)

        beforeEach {
            val authUser = AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE")
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(authUser, null, emptyList())
        }

        afterEach {
            SecurityContextHolder.clearContext()
        }

        describe("createDeck") {
            it("덱 생성 후 200과 생성된 덱 정보를 반환한다") {
                val request = CreateDeckRequest(name = "새 덱", description = "설명")
                every { deckService.createDeck(1L, any()) } returns CreateDeckResponseDto(id = 1L, name = "새 덱", description = "설명")

                val response = controller.createDeck(request)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                with(response.body!!.data!!) {
                    id shouldBe 1L
                    deckName shouldBe "새 덱"
                    deckDescription shouldBe "설명"
                }
            }
        }

        describe("deleteDeck") {
            it("덱 삭제 후 200과 삭제된 덱 id를 반환한다") {
                every { deckService.deleteDeck(1L, any()) } returns DeleteDeckResponseDto(id = 1L)

                val response = controller.deleteDeck(1L)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.id shouldBe 1L
            }
        }

        describe("updateDeck") {
            it("덱 수정 후 200과 수정된 덱 정보를 반환한다") {
                val request = UpdateDeckRequest(name = "새 이름", description = "새 설명")
                every { deckService.updateDeck(1L, any()) } returns UpdateDeckResponseDto(id = 1L, name = "새 이름", description = "새 설명")

                val response = controller.updateDeck(1L, request)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                with(response.body!!.data!!) {
                    id shouldBe 1L
                    deckName shouldBe "새 이름"
                    deckDescription shouldBe "새 설명"
                }
            }
        }
    })
