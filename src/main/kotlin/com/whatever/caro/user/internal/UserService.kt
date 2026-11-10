package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserInfo
import com.whatever.caro.user.exception.InvalidBirthDateException
import com.whatever.caro.user.exception.NicknameDuplicatedException
import com.whatever.caro.user.exception.UserNotFoundException
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val clock: Clock,
    private val emailHasher: EmailHasher,
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    transactionManager: PlatformTransactionManager,
) : UserApi {
    private val socialUserTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

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
            isAdult = user.isAdult,
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

    override fun createSocialUser(
        provider: SocialProvider,
        providerUserId: String,
        email: String?,
    ): UserInfo = createSocialUser(provider, providerUserId, email, attemptsLeft = 3)

    private fun createSocialUser(
        provider: SocialProvider,
        providerUserId: String,
        email: String?,
        attemptsLeft: Int,
    ): UserInfo =
        try {
            requireNotNull(
                socialUserTransaction.execute {
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
                    userRepository.flush()
                    user.toInfo()
                },
            )
        } catch (exception: DataIntegrityViolationException) {
            when {
                exception.isConstraintViolation("uk_users_active_nickname") && attemptsLeft > 1 ->
                    createSocialUser(provider, providerUserId, email, attemptsLeft - 1)

                exception.isConstraintViolation("uk_provider_user") ->
                    socialUserTransaction.execute { findBySocialProvider(provider, providerUserId) } ?: throw exception

                else -> throw exception
            }
        }

    @Transactional
    override fun completeRegistration(
        userId: Long,
        nickname: String,
        isTermsAgreed: Boolean,
        birthDate: LocalDate?,
        timezone: ZoneId,
    ): UserInfo {
        require(isTermsAgreed) { "약관 동의가 필요합니다" }

        val user = userRepository.findByIdForUpdate(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

        if (user.isDeleted) throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

        val adult = birthDate?.let {
            val today = clock.instant().atZone(timezone).toLocalDate()
            if (it.year < 1 || it.isAfter(today)) throw InvalidBirthDateException("유효하지 않은 생년월일입니다")
            !it.plusYears(18).isAfter(today)
        }
        user.completeRegistration(
            nickname = nickname,
            isNicknameDuplicated = { nickname -> !isNicknameAvailable(nickname) },
        )
        user.isAdult = adult
        flushNicknameChange()
        return user.toInfo()
    }

    @Transactional
    fun updateNickname(
        userId: Long,
        nickname: String,
    ): UserInfo {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        if (user.isDeleted) throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

        user.updateNickname(
            nickname = nickname,
            isNicknameDuplicated = { nickname -> !isNicknameAvailable(nickname) },
        )
        flushNicknameChange()
        return user.toInfo()
    }

    private fun flushNicknameChange() {
        try {
            userRepository.flush()
        } catch (exception: DataIntegrityViolationException) {
            if (exception.isConstraintViolation("uk_users_active_nickname")) {
                throw NicknameDuplicatedException("이미 사용 중인 닉네임입니다")
            }
            throw exception
        }
    }

    @Transactional
    override fun deleteMe(
        userId: Long,
    ) {
        val user = userRepository.findByIdForUpdate(userId) ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
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
        if (!NicknameValidator.isValid(nickname)) return false
        return !userRepository.existsByNicknameAndDeletedAtIsNull(nickname)
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

private fun DataIntegrityViolationException.isConstraintViolation(
    constraint: String,
): Boolean =
    generateSequence<Throwable>(this) { it.cause }
        .filterIsInstance<ConstraintViolationException>()
        .any { it.constraintName?.substringAfterLast('.') == constraint }
