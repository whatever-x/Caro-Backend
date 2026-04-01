package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserInfo
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.AlreadyCompletedException
import com.whatever.caro.user.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

// TODO 등록 완료 시 랜덤 닉네임으로 전환 필요
@Service
class UserService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
) : UserApi {

    @Transactional(readOnly = true)
    override fun findById(
        userId: Long,
    ): UserInfo? {
        val user = userRepository.findByIdOrNull(userId)
        return user?.toInfo()
    }

    @Transactional(readOnly = true)
    override fun findBySocialProvider(
        provider: SocialProvider,
        providerUserId: String,
    ): UserInfo? {
        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
        return socialAccount?.user?.toInfo()
    }

    @Transactional
    override fun createSocialUser(
        provider: SocialProvider,
        providerUserId: String,
        email: String?,
    ): UserInfo {
        try {
            val user = User(nickname = "user_" + providerUserId.take(8))
            userRepository.save(user)

            val socialAccount = SocialAccount(
                user = user,
                provider = provider,
                providerUserId = providerUserId,
                email = email,
            )
            socialAccountRepository.save(socialAccount)

            return socialAccount.user.toInfo()
        } catch (e: DataIntegrityViolationException) {
            logger.error { "Race condition detected for provider=$provider, providerUserId=$providerUserId" }
            return findBySocialProvider(provider, providerUserId) ?: throw e
        }
    }

    @Transactional
    override fun completeRegistration(
        userId: Long,
        nickname: String,
        isTermsAgreed: Boolean,
    ): UserInfo {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw NotFoundException("User not found: $userId")

        if (user.status == UserStatus.ACTIVE) {
            throw AlreadyCompletedException("이미 가입이 완료된 사용자입니다")
        }

        user.apply {
            this.nickname = nickname
            this.isTermsAgreed = isTermsAgreed
            this.status = UserStatus.ACTIVE
        }

        return user.toInfo()
    }
}

private fun User.toInfo(): UserInfo =
    UserInfo(
        id = id,
        nickname = nickname,
        status = status,
        isTermsAgreed = isTermsAgreed,
    )
