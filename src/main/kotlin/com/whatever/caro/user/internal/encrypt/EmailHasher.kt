package com.whatever.caro.user.internal.encrypt

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class EmailHasher(
    private val encryptorProperties: EmailEncryptorProperties,
) {
    private val keyBytes = Base64.getDecoder().decode(encryptorProperties.blindIndexKey)
    private val key = SecretKeySpec(keyBytes, ALGORITHM)

    fun hash(
        email: String?,
    ): String? {
        if (email.isNullOrBlank()) return null
        val lowercaseEmail = email.trim().lowercase()
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)

        val hash = mac.doFinal(lowercaseEmail.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }
}
