package com.whatever.caro.user.internal.web.response

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.internal.MyInfo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "MyInfoResponse", description = "내 정보 조회 응답")
data class MyInfoResponse(
    @field:Schema(description = "닉네임", example = "다정한 고슴도치")
    val nickname: String,
    @field:Schema(
        description = "이메일. 소셜 제공자가 미제공한 경우 null",
        example = "user@example.com",
        nullable = true,
    )
    val email: String?,
    @field:Schema(description = "유저가 가입한 로그인 플랫폼", example = "GOOGLE")
    val loginPlatform: SocialProvider,
)

fun MyInfo.toResponse(): MyInfoResponse =
    MyInfoResponse(
        nickname = nickname,
        email = email,
        loginPlatform = socialProvider,
    )
