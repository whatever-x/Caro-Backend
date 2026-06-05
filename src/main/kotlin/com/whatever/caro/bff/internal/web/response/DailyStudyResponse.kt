package com.whatever.caro.bff.internal.web.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.whatever.caro.bff.internal.StudyCardItem
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "일일학습 시작 정보. type에 따라 다른 schema를 반환하며, 정상적인 case라면 IN_PROGRESS만 사용된다.",
    discriminatorProperty = "type",
    discriminatorMapping = [
        DiscriminatorMapping(value = "IN_PROGRESS", schema = DailyStudyResponse.InProgress::class),
        DiscriminatorMapping(value = "COMPLETED", schema = DailyStudyResponse.Completed::class),
        DiscriminatorMapping(value = "REST_DAY", schema = DailyStudyResponse.RestDay::class),
    ],
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    Type(value = DailyStudyResponse.InProgress::class, name = "IN_PROGRESS"),
    Type(value = DailyStudyResponse.Completed::class, name = "COMPLETED"),
    Type(value = DailyStudyResponse.RestDay::class, name = "REST_DAY"),
)
sealed interface DailyStudyResponse {
    @get:Schema(description = "스키마 판별자, 시작하려는 일일학습의 상태")
    val type: DailyStudyType

    @Schema(description = "학습 시작이 가능하며 카드 목록을 포함한다")
    data class InProgress(
        val sessionId: Long,
        val studiedCardCount: Int,
        val totalCardCount: Int,
        val cards: List<StudyCardItem>,
    ) : DailyStudyResponse {
        override val type: DailyStudyType
            get() = DailyStudyType.IN_PROGRESS
    }

    @Schema(description = "이미 완료된 학습")
    data class Completed(
        val sessionId: Long,
        val studiedCardCount: Int,
        val totalCardCount: Int,
    ) : DailyStudyResponse {
        override val type: DailyStudyType
            get() = DailyStudyType.COMPLETED
    }

    @Schema(description = "휴식, 학습할 카드가 없음")
    data object RestDay : DailyStudyResponse {
        override val type: DailyStudyType
            get() = DailyStudyType.REST_DAY
    }
}

enum class DailyStudyType {
    IN_PROGRESS,
    COMPLETED,
    REST_DAY,
}
