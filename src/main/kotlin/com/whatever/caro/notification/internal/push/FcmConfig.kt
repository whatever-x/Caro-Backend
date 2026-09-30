package com.whatever.caro.notification.internal.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "app.fcm")
data class FcmProperties(
    /** 서비스 계정 JSON의 base64 인코딩 값 (FIREBASE_CREDENTIALS_BASE64) */
    val credentialsBase64: String = "",
)

@Configuration
class FcmConfig(
    private val fcmProperties: FcmProperties,
) {
    @Bean
    fun pushSender(): PushSender {
        if (fcmProperties.credentialsBase64.isBlank()) {
            logger.warn { "FIREBASE_CREDENTIALS_BASE64 미주입. 푸시는 실제로 발송되지 않고 로그만 남는다." }
            return LoggingPushSender()
        }

        val credentials = Base64.getDecoder()
            .decode(fcmProperties.credentialsBase64)
            .inputStream()
            .use { GoogleCredentials.fromStream(it) }

        val firebaseApp = FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build(),
                APP_NAME,
            )

        return FcmPushSender(FirebaseMessaging.getInstance(firebaseApp))
    }

    companion object {
        private const val APP_NAME = "caro"
    }
}
