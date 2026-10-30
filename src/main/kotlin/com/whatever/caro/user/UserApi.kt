package com.whatever.caro.user

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
        birthDate: LocalDate? = null,
        timezone: ZoneId = ZoneOffset.UTC,
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
