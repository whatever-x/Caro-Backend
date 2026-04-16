package com.whatever.caro.user.internal.web.response

data class NicknameCheckResponse(
    val nickname: String,
    val available: Boolean,
)
