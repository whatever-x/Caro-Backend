package com.whatever.caro.bff.internal

import java.util.Base64

/**
 * 리포트 목록의 다음 페이지 위치
 *
 * [filter]는 첫 페이지의 조회 조건이다. 다른 조건의 요청에 이 커서를 쓰면 거절한다.
 * [maxReviewId]는 첫 페이지를 읽은 시점이며, 이후 페이지도 이 시점까지의 평가만 읽어 새 평가로 목록이 밀리지 않게 한다.
 * 사용자 id는 담지 않는다. 커서를 바꿔도 조회는 항상 인증된 사용자의 기록으로 한정된다.
 */
data class ReportCursor(
    val kind: Kind,
    val filter: String,
    val maxReviewId: Long,
    val keys: List<String>,
) {
    enum class Kind { DECKS, SESSIONS, CARDS, REVIEWS }

    fun encode(): String {
        val raw = listOf(
            VERSION,
            kind.name,
            filter,
            maxReviewId.toString(),
            *keys.toTypedArray(),
        ).joinToString(SEPARATOR)
        return ENCODER.encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val VERSION = "v1"
        private const val SEPARATOR = "|"
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        /**
         * 조회 조건을 커서에 담을 문자열로 만든다. 값에 구분자가 들어가지 않는 숫자·날짜·enum만 쓴다.
         */
        fun filterOf(
            vararg values: Any?,
        ): String = values.joinToString(",") { it?.toString() ?: "-" }

        fun decode(
            value: String,
            expectedKind: Kind,
            expectedFilter: String,
            keyCount: Int,
        ): ReportCursor {
            val parts = runCatching { String(DECODER.decode(value), Charsets.UTF_8).split(SEPARATOR) }
                .getOrElse { throw InvalidReportCursorException("cursor is not base64") }
            if (parts.size != 4 + keyCount || parts[0] != VERSION) {
                throw InvalidReportCursorException("cursor has unexpected format")
            }
            if (parts[1] != expectedKind.name || parts[2] != expectedFilter) {
                throw InvalidReportCursorException("cursor belongs to another list or filter")
            }
            val maxReviewId = parts[3].toLongOrNull()?.takeIf { it >= 0 }
                ?: throw InvalidReportCursorException("cursor has invalid snapshot")
            return ReportCursor(expectedKind, expectedFilter, maxReviewId, parts.drop(4))
        }
    }
}
