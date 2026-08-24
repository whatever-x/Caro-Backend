package com.whatever.caro.user.internal

import com.whatever.caro.common.entity.SoftDeletableEntity
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.internal.encrypt.EmailCryptoConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Clock
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var nickname: String,

    @Convert(converter = EmailCryptoConverter::class)
    @Column(name = "encrypted_primary_email", nullable = true)
    var encryptedPrimaryEmail: String? = null,

    @Column(name = "hashed_primary_email", nullable = true)
    var hashedPrimaryEmail: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.SUSPENDED,

    @Column(name = "is_terms_agreed", nullable = false)
    var isTermsAgreed: Boolean = false,
) : SoftDeletableEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun updateNickname(
        nickname: String,
    ) {
        this.nickname = nickname
    }

    fun completeRegistration(
        nickname: String,
    ) {
        updateNickname(nickname)
        this.isTermsAgreed = true
        this.status = UserStatus.ACTIVE
    }

    fun deleteUserInfo(
        clock: Clock,
    ) {
        if (isDeleted) return
        val now = Instant.now(clock)
        softDelete(deletedAt = now)
    }
}
