package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserInfo
import com.whatever.caro.user.exception.UserNotFoundException
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val clock: Clock,
    private val emailHasher: EmailHasher,
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
) : UserApi {

    @Transactional(readOnly = true)
    fun getUserInfo(
        userId: Long,
    ): MyInfo {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        if (user.isDeleted) {
            throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        }

        val socialAccount = socialAccountRepository.findByUserId(userId)
            ?: run {
                logger.error { "Social account not found for userId=$userId" }
                throw IllegalStateException("소셜 계정이 없는 사용자: $userId") // TODO CustomException
            }

        return MyInfo(
            nickname = user.nickname,
            email = user.encryptedPrimaryEmail,
            socialProvider = socialAccount.provider,
        )
    }

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
            val hashedSocialEmail = emailHasher.hash(email)
            val user = User(
                nickname = tempNickname,
                isTermsAgreed = false,
                encryptedPrimaryEmail = email,
                hashedPrimaryEmail = hashedSocialEmail,
            )
            userRepository.save(user)

            val socialAccount = SocialAccount(
                user = user,
                provider = provider,
                providerUserId = providerUserId,
                encryptedEmail = email,
                hashedEmail = hashedSocialEmail,
            )
            socialAccountRepository.save(socialAccount)

            return socialAccount.user.toInfo()
        } catch (e: DataIntegrityViolationException) {
            // NOTE: 이메일 hash UNIQUE(users.active_hashed_primary_email, social_accounts.hashed_email) 추가로
            //  이 예외가 이제 두 의미를 가짐 - ① 소셜계정(provider+providerUserId) 중복 race ② 동일 이메일 중복.
            //  현재 복구는 ①만 처리(provider로 재조회). 멀티 provider 같은 이메일 가입(②)은 여기서 못 찾아 re-throw됨.
            //  TODO: 계정 연동(account linking) 정책 확정 시 제약명으로 원인 구분하여 분기.
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
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

        user.completeRegistration(
            nickname = nickname,
            isNicknameDuplicated = { nickname -> !isNicknameAvailable(nickname) },
        )
        return user.toInfo()
    }

    @Transactional
    fun updateNickname(
        userId: Long,
        nickname: String,
    ): UserInfo {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

        user.updateNickname(
            nickname = nickname,
            isNicknameDuplicated = { nickname -> !isNicknameAvailable(nickname) },
        )
        return user.toInfo()
    }

    @Transactional
    override fun deleteMe(
        userId: Long,
    ) {
        val user = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        user.deleteUserInfo(clock = clock)
    }

    @Transactional(readOnly = true)
    override fun findWithdrawnUserIds(
        limit: Int,
    ): List<Long> = userRepository.findWithdrawnUserIds(PageRequest.of(0, limit))

    @Transactional
    override fun hardDeleteUser(
        userId: Long,
    ) {
        // FK 순서: social_accounts(자식, user_id) → users(부모).
        socialAccountRepository.hardDeleteAllByUserId(userId)
        userRepository.hardDeleteById(userId)
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
        isDeleted = isDeleted,
    )

object NicknameValidator {
    val regex = "^(?=.{2,20}$)[a-zA-Z0-9가-힣](?:[a-zA-Z0-9가-힣]|[-_][a-zA-Z0-9가-힣])*$".toRegex()
    fun isValid(
        nickname: String,
    ): Boolean = nickname.matches(regex) && nickname.isNotBlank()
}
