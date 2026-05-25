package com.whatever.caro.auth.internal.web.request

import com.whatever.caro.user.SocialProvider
import jakarta.validation.constraints.NotBlank

data class SocialLoginRequest(
    val provider: SocialProvider,

    @field:NotBlank(message = "ID Token은 필수입니다")
    val idToken: String,
)
