package com.whatever.caro.withdrawal.internal

import com.whatever.caro.user.UserApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class WithdrawnUserPurgeSweeper(
    private val userApi: UserApi,
    private val purgeService: WithdrawnUserPurgeService,
) {

    /**
     * 탈퇴한 유저를 한 배치(BATCH_SIZE)씩 파기한다. 유예 기간 없이 다음 스케줄에 바로 파기 대상이 된다.
     * cron 미설정(빈 문자열) 시 스케줄이 등록되지 않아 동작하지 않는다.
     *
     * 유저별 파기는 [WithdrawnUserPurgeService.purge] 의 독립 트랜잭션에서 수행하고,
     * 한 유저가 실패해도 로그만 남기고 다음 유저로 진행한다. 실패 유저는 탈퇴 상태로 남아
     * 다음 스케줄에 재시도된다(단일 배치 처리라 실패 유저로 인한 무한 루프가 없다).
     */
    @Scheduled(cron = $$"${app.schedule.cron.withdrawn-user-purge:-}")
    fun purgeWithdrawnUsers() {
        val targetUserIds = userApi.findWithdrawnUserIds(BATCH_SIZE)
        if (targetUserIds.isEmpty()) return

        var purged = 0
        for (userId in targetUserIds) {
            runCatching { purgeService.purge(userId) }
                .onSuccess { purged++ }
                .onFailure { e -> logger.error(e) { "Withdrawn user purge failed. userId=$userId" } }
        }
        logger.info { "Withdrawn user purge done. target=${targetUserIds.size} purged=$purged" }
    }

    companion object {
        private const val BATCH_SIZE = 100
    }
}
