package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider

data class MyInfo(
    val nickname: String,
    val email: String?,
    val socialProvider: SocialProvider,
)
