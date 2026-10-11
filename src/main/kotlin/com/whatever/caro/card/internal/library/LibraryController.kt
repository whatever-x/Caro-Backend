package com.whatever.caro.card.internal.library

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Library", description = "제공 덱 조회와 개인 덱 추가")
@RestController
@RequestMapping("/library/decks")
class LibraryController(
    private val service: LibraryService,
) {
    @Operation(summary = "게시된 제공 덱 목록")
    @GetMapping(version = "1.0")
    fun list(): ResponseEntity<ApiResponse<List<LibraryDeckResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(service.list()))

    @Operation(summary = "제공 덱 미리보기")
    @GetMapping("/{libraryDeckId}", version = "1.0")
    fun detail(
        @Positive @PathVariable libraryDeckId: Long,
    ): ResponseEntity<ApiResponse<LibraryDetailResponse>> =
        ResponseEntity.ok(ApiResponse.ok(service.detail(libraryDeckId)))

    @Operation(summary = "제공 덱을 새 개인 덱으로 추가")
    @PostMapping("/{libraryDeckId}/copies", version = "1.0")
    fun copy(
        @Positive @PathVariable libraryDeckId: Long,
        @RequestHeader("Idempotency-Key") requestKey: UUID,
    ): ResponseEntity<ApiResponse<LibraryCopyResponse>> =
        ResponseEntity.status(
            201,
        ).body(ApiResponse.ok(service.copy(SecurityUtil.currentUser().userId, libraryDeckId, requestKey)))
}
