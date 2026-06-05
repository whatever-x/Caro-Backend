package com.whatever.caro.study.internal.web.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.whatever.caro.study.TodaySummaryState
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "일일학습 요약 정보. type에 따라 다른 schema를 반환한다.",
    discriminatorProperty = "type",
    discriminatorMapping = [
        DiscriminatorMapping(value = "NOT_STARTED", schema = DailyStudySummaryResponse.NotStarted::class),
        DiscriminatorMapping(value = "IN_PROGRESS", schema = DailyStudySummaryResponse.InProgress::class),
        DiscriminatorMapping(value = "COMPLETED", schema = DailyStudySummaryResponse.Completed::class),
        DiscriminatorMapping(value = "REST_DAY", schema = DailyStudySummaryResponse.RestDay::class),
    ],
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    Type(value = DailyStudySummaryResponse.NotStarted::class, name = "NOT_STARTED"),
    Type(value = DailyStudySummaryResponse.InProgress::class, name = "IN_PROGRESS"),
    Type(value = DailyStudySummaryResponse.Completed::class, name = "COMPLETED"),
    Type(value = DailyStudySummaryResponse.RestDay::class, name = "REST_DAY"),
)
sealed interface DailyStudySummaryResponse {
    val type: TodaySummaryState

    data class NotStarted(
        val studiedCardCount: Int,
        val totalCardCount: Int,
    ) : DailyStudySummaryResponse {
        override val type
            get() = TodaySummaryState.NOT_STARTED
    }

    data class InProgress(
        val sessionId: Long,
        val studiedCardCount: Int,
        val totalCardCount: Int,
    ) : DailyStudySummaryResponse {
        override val type
            get() = TodaySummaryState.IN_PROGRESS
    }

    data class Completed(
        val sessionId: Long,
        val studiedCardCount: Int,
        val totalCardCount: Int,
    ) : DailyStudySummaryResponse {
        override val type
            get() = TodaySummaryState.COMPLETED
    }

    data object RestDay : DailyStudySummaryResponse {
        override val type
            get() = TodaySummaryState.REST_DAY
    }
}
