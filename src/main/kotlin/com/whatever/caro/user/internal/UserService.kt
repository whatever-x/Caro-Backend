package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserInfo
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.AlreadyCompletedException
import com.whatever.caro.user.exception.NicknameDuplicatedException
import com.whatever.caro.user.exception.UserNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
) : UserApi {

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
            val tempNickname = "temp_${UUID.randomUUID().toString().take(8)}"
            val user = User(
                nickname = tempNickname,
                isTermsAgreed = false,
            )
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
        require(isTermsAgreed) { "약관 동의가 필요합니다" }

        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException("User not found: $userId")

        if (user.status == UserStatus.ACTIVE) {
            throw AlreadyCompletedException("이미 가입이 완료된 사용자입니다")
        }

        if (!isNicknameAvailable(nickname)) {
            throw NicknameDuplicatedException("이미 사용 중인 닉네임입니다: $nickname")
        }

        user.apply {
            this.nickname = nickname
            this.isTermsAgreed = true
            this.status = UserStatus.ACTIVE
        }
        return user.toInfo()
    }

    override fun isNicknameAvailable(
        nickname: String,
    ): Boolean {
        val isValid = NicknameValidator.isValid(nickname)
        val isExists = userRepository.existsByNicknameAndDeletedAtIsNull(nickname)
        return isValid && !isExists
    }
}

private fun User.toInfo(): UserInfo =
    UserInfo(
        id = id,
        nickname = nickname,
        status = status,
        isTermsAgreed = isTermsAgreed,
    )

object NicknameValidator {
    val regex = "^(?=.{2,20}$)[a-zA-Z0-9가-힣](?:[a-zA-Z0-9가-힣]|[-_][a-zA-Z0-9가-힣])*$".toRegex()
    fun isValid(
        nickname: String,
    ): Boolean = nickname.matches(regex) && nickname.isNotBlank()
}
