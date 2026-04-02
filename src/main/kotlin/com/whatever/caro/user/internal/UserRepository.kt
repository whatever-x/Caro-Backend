package com.whatever.caro.user.internal

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun existsByNicknameAndDeletedAtIsNull(
        nickname: String,
    ): Boolean
}
