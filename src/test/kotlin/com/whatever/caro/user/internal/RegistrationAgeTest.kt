package com.whatever.caro.user.internal

import com.whatever.caro.user.exception.InvalidBirthDateException
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RegistrationAgeTest :
    DescribeSpec({
        val repository = mockk<UserRepository>()
        val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
        val service = UserService(clock, mockk<EmailHasher>(), repository, mockk<SocialAccountRepository>())

        describe("가입 시 연령 판단") {
            it("0년 생일은 거부한다") {
                val user = User(nickname = "before")
                every { repository.findForRegistration(3L) } returns user
                shouldThrow<InvalidBirthDateException> {
                    service.completeRegistration(3L, "invalid", true, LocalDate.of(0, 1, 1))
                }
                user.isAdult shouldBe null
            }
            it("한국 시간으로 생일이 시작되면 성인으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findForRegistration(4L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("birthday") } returns false
                val seoulMidnight = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneOffset.UTC)
                val birthdayService = UserService(seoulMidnight, mockk<EmailHasher>(), repository, mockk<SocialAccountRepository>())
                birthdayService.completeRegistration(4L, "birthday", true, LocalDate.of(2008, 10, 28), ZoneId.of("Asia/Seoul"))
                user.isAdult shouldBe true
            }
            it("만 18세 생일부터 성인으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findForRegistration(1L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("adult") } returns false
                service.completeRegistration(1L, "adult", true, LocalDate.of(2008, 10, 28))
                user.isAdult shouldBe true
            }
            it("만 18세 생일 하루 전은 미성년으로 저장한다") {
                val user = User(nickname = "before")
                every { repository.findForRegistration(2L) } returns user
                every { repository.existsByNicknameAndDeletedAtIsNull("minor") } returns false
                service.completeRegistration(2L, "minor", true, LocalDate.of(2008, 10, 29))
                user.isAdult shouldBe false
            }
        }
    })
