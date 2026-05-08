package com.whatever.caro.nickname.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "NicknameResponse", description = "랜덤 닉네임 응답")
data class NicknameResponse(
    @field:Schema(description = "추천 닉네임", example = "다정한 고슴도치")
    val nickname: String,
)
