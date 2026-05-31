package com.whatever.caro.user

interface UserApi {
    fun findById(
        userId: Long,
    ): UserInfo?

    fun findBySocialProvider(
        provider: SocialProvider,
        providerUserId: String,
    ): UserInfo?

    fun createSocialUser(
        provider: SocialProvider,
        providerUserId: String,
        email: String?,
    ): UserInfo

    fun completeRegistration(
        userId: Long,
        nickname: String,
        isTermsAgreed: Boolean,
    ): UserInfo

    fun updateNickname(
        userId: Long,
        nickname: String,
    ): UserInfo

    fun isNicknameAvailable(
        nickname: String,
    ): Boolean
}
