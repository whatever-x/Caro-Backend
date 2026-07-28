package com.whatever.caro.withdrawal.internal

import com.whatever.caro.user.UserApi
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify

class WithdrawnUserPurgeSweeperUnitTest :
    DescribeSpec({

        val userApi = mockk<UserApi>()
        val purgeService = mockk<WithdrawnUserPurgeService>()
        val sweeper = WithdrawnUserPurgeSweeper(userApi, purgeService)

        beforeEach {
            // spec 레벨 공유 mock의 호출 기록을 테스트마다 초기화
            clearMocks(userApi, purgeService)
        }

        describe("purgeWithdrawnUsers") {
            it("한 유저 파기가 실패해도 로그만 남기고 나머지 유저는 계속 파기한다") {
                every { userApi.findWithdrawnUserIds(any()) } returns listOf(1L, 2L, 3L)
                every { purgeService.purge(1L) } just Runs
                every { purgeService.purge(2L) } throws RuntimeException("boom")
                every { purgeService.purge(3L) } just Runs

                sweeper.purgeWithdrawnUsers()

                verify(exactly = 1) { purgeService.purge(1L) }
                verify(exactly = 1) { purgeService.purge(2L) }
                verify(exactly = 1) { purgeService.purge(3L) }
            }

            it("탈퇴 대상이 없으면 파기를 호출하지 않는다") {
                every { userApi.findWithdrawnUserIds(any()) } returns emptyList()

                sweeper.purgeWithdrawnUsers()

                verify(exactly = 0) { purgeService.purge(any()) }
            }
        }
    })
