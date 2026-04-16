package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    @Query(
        "SELECT sa FROM SocialAccount sa JOIN FETCH sa.user WHERE sa.provider = :provider AND sa.providerUserId = :providerUserId",
    )
    fun findByProviderAndProviderUserId(
        provider: SocialProvider,
        providerUserId: String,
    ): SocialAccount?
}
