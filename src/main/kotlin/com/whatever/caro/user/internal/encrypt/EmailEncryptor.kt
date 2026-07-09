package com.whatever.caro.user.internal.encrypt

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class EmailEncryptor(
    private val encryptorProperties: EmailEncryptorProperties,
) {
    private val keyBytes = Base64.getDecoder().decode(encryptorProperties.encryptionKey)
    private val key = SecretKeySpec(keyBytes, "AES")
    private val secureRandom = SecureRandom()

    fun encrypt(
        email: String?,
    ): String? {
        if (email.isNullOrBlank()) return null
        // 1. iv 생성
        val iv = ByteArray(IV_LENGTH).also { bytes -> secureRandom.nextBytes(bytes) }

        // 2. email key로 암호화
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        // 3. Base64(keyVersion + iv + [cipher + tag] -> 이건 doFinal이 만들어줌)
        val securedEmail = byteArrayOf(KEY_VERSION) + iv + cipher.doFinal(email.toByteArray())
        val encryptedEmail = Base64.getEncoder().encodeToString(securedEmail)
        return encryptedEmail
    }

    fun decrypt(
        encryptedEmail: String?,
    ): String? {
        if (encryptedEmail.isNullOrBlank()) return null
        // base 64 decode
        val plainText = runCatching {
            val decodedRaw = Base64.getDecoder().decode(encryptedEmail)
            val version = decodedRaw[0] // 당장은 안씀

            // iv 뜯어내고
            val iv = decodedRaw.copyOfRange(1, 1 + IV_LENGTH)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            // cipher text + tag
            val body = decodedRaw.copyOfRange(1 + IV_LENGTH, decodedRaw.size)

            // decrypt (tag 까지 같이 검증)
            cipher.doFinal(body)
        }.getOrElse { throwable ->
            throw EmailDecryptionException("이메일 복호화 실패", throwable)
        }
        return String(plainText, Charsets.UTF_8)
    }

    companion object {
        private const val KEY_VERSION: Byte = 1
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
    }
}
