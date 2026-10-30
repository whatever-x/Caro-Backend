package com.whatever.caro.auth.internal.web.request

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CompleteRegistrationV11Request(
    @field:NotBlank(message = "닉네임은 필수입니다")
    @field:Size(max = 50, message = "닉네임은 50자 이하여야 합니다")
    val nickname: String,
    @get:JsonProperty("isTermsAgreed")
    @field:AssertTrue(message = "약관에 동의해야 합니다")
    @get:Schema(description = "약관 동의 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    val isTermsAgreed: Boolean,
    @field:Schema(description = "생년월일. 성인 여부 판정에만 사용하며 저장하지 않는다", requiredMode = Schema.RequiredMode.REQUIRED)
    val birthDate: LocalDate,
)
