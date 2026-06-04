package com.whatever.caro.user.internal.web.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(name = "UpdateNicknameRequest", description = "닉네임 변경 요청")
data class UpdateNicknameRequest(
    @field:NotBlank(message = "Nickname is required")
    @field:Size(min = 1, max = 50, message = "Nickname must be 1-50 characters")
    @field:Schema(description = "변경할 닉네임", example = "다정한 고슴도치", required = true)
    val nickname: String,
)
