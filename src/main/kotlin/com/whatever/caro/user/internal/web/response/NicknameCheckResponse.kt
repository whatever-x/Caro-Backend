package com.whatever.caro.user.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "NicknameCheckResponse", description = "닉네임 사용 가능 여부 응답")
data class NicknameCheckResponse(
    @field:Schema(description = "조회 대상 닉네임", example = "다정한 고슴도치")
    val nickname: String,
    @field:Schema(description = "사용 가능 여부 (true=사용 가능, false=중복)", example = "true")
    val available: Boolean,
)
