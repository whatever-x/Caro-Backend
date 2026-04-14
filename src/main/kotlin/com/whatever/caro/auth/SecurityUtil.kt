package com.whatever.caro.auth

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import org.springframework.security.core.context.SecurityContextHolder

object SecurityUtil {
    fun currentUser(): AuthUser {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidAccessTokenException("인증 정보가 없습니다")

        return authentication.principal as? AuthUser
            ?: throw InvalidAccessTokenException("잘못된 인증 정보입니다")
    }
}
