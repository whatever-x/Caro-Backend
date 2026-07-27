package com.whatever.caro.user.internal

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun existsByNicknameAndDeletedAtIsNull(
        nickname: String,
    ): Boolean

    /**
     * 탈퇴(soft delete)한 유저 id를 오래된 순으로 조회한다.
     */
    @Query(
        "select u.id from User u where u.deletedAt is not null order by u.deletedAt asc",
    )
    fun findWithdrawnUserIds(
        pageable: Pageable,
    ): List<Long>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from User u where u.id = :userId")
    fun hardDeleteById(
        userId: Long,
    ): Int
}
