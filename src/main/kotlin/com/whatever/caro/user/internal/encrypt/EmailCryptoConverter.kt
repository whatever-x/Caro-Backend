package com.whatever.caro.user.internal.encrypt

import jakarta.persistence.AttributeConverter
import org.springframework.stereotype.Component

@Component
class EmailCryptoConverter(
    private val encryptor: EmailEncryptor,
) : AttributeConverter<String?, String?> {

    override fun convertToDatabaseColumn(
        email: String?,
    ): String? = encryptor.encrypt(email)

    override fun convertToEntityAttribute(
        encryptedEmail: String?,
    ): String? = encryptor.decrypt(encryptedEmail)
}
