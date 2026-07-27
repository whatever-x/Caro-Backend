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

    fun isNicknameAvailable(
        nickname: String,
    ): Boolean

    fun deleteMe(
        userId: Long,
    )

    fun findWithdrawnUserIds(
        limit: Int,
    ): List<Long>

    fun hardDeleteUser(
        userId: Long,
    )
}
