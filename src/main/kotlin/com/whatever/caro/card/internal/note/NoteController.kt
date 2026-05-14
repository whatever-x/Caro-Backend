package com.whatever.caro.card.internal.note

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.note.dto.create.CreateNoteRequest
import com.whatever.caro.card.internal.note.dto.create.CreateNoteResponse
import com.whatever.caro.card.internal.note.dto.create.toDto
import com.whatever.caro.card.internal.note.dto.create.toResponse
import com.whatever.caro.card.internal.note.dto.read.NoteWithCardsResponse
import com.whatever.caro.card.internal.note.dto.read.toResponse
import com.whatever.caro.card.internal.note.dto.delete.DeleteNoteDto
import com.whatever.caro.card.internal.note.dto.delete.DeleteNoteResponse
import com.whatever.caro.card.internal.note.dto.delete.toResponse
import com.whatever.caro.card.internal.note.dto.update.UpdateNoteRequest
import com.whatever.caro.card.internal.note.dto.update.UpdateNoteResponse
import com.whatever.caro.card.internal.note.dto.update.toDto
import com.whatever.caro.card.internal.note.dto.update.toResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
class NoteController(
    private val noteService: NoteService,
) {
    @PostMapping("/v1/decks/{deckId}/cards")
    fun createNote(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
        @Valid @RequestBody request: CreateNoteRequest,
    ): ResponseEntity<ApiResponse<CreateNoteResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.createNote(userId = userId, dto = request.toDto(deckId)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/v1/decks/{deckId}/cards")
    fun getNotes(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<List<NoteWithCardsResponse>>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.getNotesByDeck(userId = userId, deckId = deckId).map { it.toResponse() }
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/v1/cards/{id}")
    fun getCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<NoteWithCardsResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.getNoteById(userId = userId, noteId = id).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PatchMapping("/v1/cards/{id}")
    fun updateNote(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
        @Valid @RequestBody request: UpdateNoteRequest,
    ): ResponseEntity<ApiResponse<UpdateNoteResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.updateNote(userId = userId, dto = request.toDto(noteId = id)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @DeleteMapping("/v1/cards/{id}")
    fun deleteNote(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<DeleteNoteResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = noteService.deleteNote(userId = userId, dto = DeleteNoteDto(noteId = id)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
