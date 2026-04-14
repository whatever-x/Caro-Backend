package com.whatever.caro.auth.internal.social

import com.whatever.caro.user.SocialProvider

interface SocialIdTokenVerifier {
    val provider: SocialProvider

    fun verify(
        idToken: String,
    ): SocialUserInfo
}
