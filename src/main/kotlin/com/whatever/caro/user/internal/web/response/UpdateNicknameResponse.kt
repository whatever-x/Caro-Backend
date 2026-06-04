package com.whatever.caro.user.internal.web.response

import com.whatever.caro.user.UserInfo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "UpdateNicknameResponse", description = "닉네임 변경 응답")
data class UpdateNicknameResponse(
    @field:Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @field:Schema(description = "변경된 닉네임", example = "다정한 고슴도치")
    val nickname: String,
)

fun UserInfo.toUpdateNicknameResponse(): UpdateNicknameResponse =
    UpdateNicknameResponse(
        userId = id,
        nickname = nickname,
    )
