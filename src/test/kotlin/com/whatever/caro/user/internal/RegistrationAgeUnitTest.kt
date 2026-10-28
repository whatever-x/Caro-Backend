package com.whatever.caro.user.internal

import com.whatever.caro.user.exception.InvalidBirthDateException
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RegistrationAgeUnitTest :
    DescribeSpec({
        val repository = mockk<UserRepository>()
        justRun { repository.flush() }
        val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
        val service = UserService(clock, mockk<EmailHasher>(), repository, mockk<SocialAccountRepository>(), mockk<PlatformTransactionManager>(relaxed = true))

        describe("닉네임 사전 검증") {
            it("유효하지 않은 닉네임은 DB를 조회하지 않는다") {
                val nicknameRepository = mockk<UserRepository>()
                val nicknameService = UserService(clock, mockk<EmailHasher>(), nicknameRepository, mockk<SocialAccountRepository>(), mockk<PlatformTransactionManager>(relaxed = true))
                nicknameService.isNicknameAvailable("invalid nickname") shouldBe false
                verify(exactly = 0) { nicknameRepository.existsByNicknameAndDeletedAtIsNull(any()) }
            }
        }

        describe("가입 시 연령 판단") {
            it("0년 생일은 거부한다") {
                val user = User(nickname = "before")
                every { repository.findByIdForUpdate(3L) } returns user
                shouldThrow<InvalidBirthDateException> {
                    service.completeRegistration(3L, "invalid", true, LocalDate.of(0, 1, 1))
                }
                user.isAdult shouldBe null
            }
            it("한국 시간으로 생일이 시작되면 성인으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findByIdForUpdate(4L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("birthday") } returns false
                val seoulMidnight = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneOffset.UTC)
                val birthdayService = UserService(seoulMidnight, mockk<EmailHasher>(), repository, mockk<SocialAccountRepository>(), mockk<PlatformTransactionManager>(relaxed = true))
                birthdayService.completeRegistration(4L, "birthday", true, LocalDate.of(2008, 10, 28), ZoneId.of("Asia/Seoul"))
                user.isAdult shouldBe true
            }
            it("만 18세 생일부터 성인으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findByIdForUpdate(1L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("adult") } returns false
                service.completeRegistration(1L, "adult", true, LocalDate.of(2008, 10, 28))
                user.isAdult shouldBe true
            }
            it("만 18세 생일 하루 전은 미성년으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findByIdForUpdate(2L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("minor") } returns false
                service.completeRegistration(2L, "minor", true, LocalDate.of(2008, 10, 29))
                user.isAdult shouldBe false
            }
        }
    })
