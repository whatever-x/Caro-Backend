package com.whatever.caro.auth.internal.social

import com.whatever.caro.user.SocialProvider

data class SocialUserInfo(
    val providerUserId: String,
    val email: String?,
    val provider: SocialProvider,
)
