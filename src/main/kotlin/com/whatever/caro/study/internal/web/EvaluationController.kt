package com.whatever.caro.study.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.web.idempotency.Idempotent
import com.whatever.caro.study.internal.EvaluatedCardDto
import com.whatever.caro.study.internal.EvaluationService
import com.whatever.caro.study.internal.web.request.EvaluatedCardRequest
import com.whatever.caro.study.internal.web.response.EvaluationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant

@Tag(name = "StudySession", description = "일일학습 세션 / 평가")
@RestController
@RequestMapping("/v1/study-sessions")
class EvaluationController(
    private val clock: Clock,
    private val evaluationService: EvaluationService,
) {

    @Operation(
        summary = "카드 평가 제출",
        description = """
        일일학습에서 사용자의 카드 평가를 제출한다.
        제출한 평가 중 일부 항목이 검증에 실패해도 성공 응답으로 반환하며,
        성공한 평가들을 `evaluatedCardIds`, 실패한 평가들은 `failedCardIds`로 구분된다.
        세션의 모든 카드를 평가했을 경우 sessionStatus를 COMPLETED로 응답한다.

        중복 요청 방지를 위해 UUIDv4인 `Idempotency-Key` 헤더를 포함해야한다.
        이 endpoint는 멱등하므로 안전한 재시도가 가능하다.
        """,
    )
    @Idempotent
    @PostMapping("/{sessionId}/evaluations")
    fun evaluate(
        @RequestHeader("Idempotency-Key", required = true) idempotencyKey: String,
        @Positive @PathVariable(required = true) sessionId: Long,
        @Valid @RequestBody items: List<EvaluatedCardRequest>,
    ): ResponseEntity<EvaluationResponse> {
        val now = Instant.now(clock)
        val result = evaluationService.evaluate(
            now = now,
            userId = SecurityUtil.currentUser().userId,
            sessionId = sessionId,
            items = items.map { it.toDto() }
        )

        return ResponseEntity.ok(EvaluationResponse.from(result))
    }
}

private fun EvaluatedCardRequest.toDto(): EvaluatedCardDto {
    return EvaluatedCardDto(
        cardId = cardId,
        rating = rating,
        timeMs = timeMs.takeIf { it > 600_000 } ?: 600_000,
    )
}
