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

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var nickname: String,

    @Column(name = "primary_email", nullable = true)
    var primaryEmail: String? = null,

    @Convert(converter = EmailCryptoConverter::class)
    @Column(name = "encrypted_primary_email", nullable = true)
    var encryptedPrimaryEmail: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.SUSPENDED,

    @Column(name = "is_terms_agreed", nullable = false)
    var isTermsAgreed: Boolean = false,
) : SoftDeletableEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
