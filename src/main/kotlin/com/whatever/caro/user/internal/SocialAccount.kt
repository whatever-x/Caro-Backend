package com.whatever.caro.user.internal

import com.whatever.caro.common.entity.BaseTimeEntity
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.internal.encrypt.EmailCryptoConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "social_accounts")
class SocialAccount(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: SocialProvider,

    @Column(name = "provider_user_id", nullable = false)
    val providerUserId: String,

    @Column
    var email: String? = null,

    @Column(name = "encrypted_email", nullable = true)
    @Convert(converter = EmailCryptoConverter::class)
    var encryptedEmail: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
