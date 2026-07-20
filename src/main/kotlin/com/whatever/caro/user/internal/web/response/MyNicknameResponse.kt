package com.whatever.caro.user.internal.web.response

import com.whatever.caro.user.UserInfo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "MyNicknameResponse", description = "내 닉네임 조회 응답")
data class MyNicknameResponse(
    @field:Schema(description = "닉네임", example = "다정한 고슴도치")
    val nickname: String,
)

fun UserInfo.toMyNicknameResponse(): MyNicknameResponse = MyNicknameResponse(nickname = nickname)
