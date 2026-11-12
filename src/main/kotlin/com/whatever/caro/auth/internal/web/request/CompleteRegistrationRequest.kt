package com.whatever.caro.auth.internal.web.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CompleteRegistrationRequest(
    @field:NotBlank(message = "{validation.nickname.required}")
    @field:Size(max = 50, message = "{validation.nickname.max_size}")
    val nickname: String,

    @get:JsonProperty("isTermsAgreed")
    @field:AssertTrue(message = "{validation.terms.required}")
    val isTermsAgreed: Boolean,
)
