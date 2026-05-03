package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.card.internal.deck.DeckService
import com.whatever.caro.card.internal.deck.dto.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.CreateDeckResponse
import com.whatever.caro.card.internal.deck.dto.toDto
import com.whatever.caro.card.internal.deck.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController(value = "/decks")
class DeckController(
    private val deckService: DeckService,
) {

    @PostMapping
    fun createDeck(
        @Valid @RequestBody createDeckRequest: CreateDeckRequest,
    ): ResponseEntity<CreateDeckResponse> {
        // val userId = "" 지금 유저 id 얻는거 있나 ?!
        val decks = deckService.createDeck(userId = 0L, createDeckRequest.toDto()).toResponse()
        return ResponseEntity.created(URI.create("/decks/${decks.id}")).body(decks)
    }
}
