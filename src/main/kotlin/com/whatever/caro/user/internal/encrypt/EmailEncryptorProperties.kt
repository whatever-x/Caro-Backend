package com.whatever.caro.user.internal.encrypt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Base64

@ConfigurationProperties(prefix = "app.email")
data class EmailEncryptorProperties(
    val encryptionKey: String,
    val blindIndexKey: String,
) {
    init {
        require(encryptionKey.isNotBlank() && !encryptionKey.startsWith($$"${")) { "EMAIL_ENCRYPTION_KEY 미주입" }
        require(Base64.getDecoder().decode(encryptionKey).size == 32) { "EMAIL_ENCRYPTION_KEY는 Base64 32바이트여야 함" }
    }
}
