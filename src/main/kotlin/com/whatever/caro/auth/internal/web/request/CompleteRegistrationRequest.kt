package com.whatever.caro.auth.internal.web.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CompleteRegistrationRequest(
    @field:NotBlank(message = "닉네임은 필수입니다")
    @field:Size(max = 50, message = "닉네임은 50자 이하여야 합니다")
    val nickname: String,

    @field:AssertTrue(message = "약관에 동의해야 합니다")
    val isTermsAgreed: Boolean,

    @field:NotBlank(message = "Device ID는 필수입니다")
    val deviceId: String,
)
