package com.whatever.caro.card.internal.note

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.note.dto.create.CreateNoteRequest
import com.whatever.caro.card.internal.note.dto.create.CreateNoteResponse
import com.whatever.caro.card.internal.note.dto.create.toDto
import com.whatever.caro.card.internal.note.dto.create.toResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
class NoteController(
    private val noteService: NoteService,
) {
    @PostMapping("/v1/decks/{deckId}/notes")
    fun createNote(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
        @Valid @RequestBody request: CreateNoteRequest,
    ): ResponseEntity<ApiResponse<CreateNoteResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.createNote(userId = userId, dto = request.toDto(deckId)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
