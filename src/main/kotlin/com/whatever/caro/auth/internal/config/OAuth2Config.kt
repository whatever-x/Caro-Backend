package com.whatever.caro.auth.internal.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OAuth2Config {

    @Bean
    fun googleVerifier(
        oauth2Properties: OAuth2Properties,
    ): GoogleIdTokenVerifier =
        GoogleIdTokenVerifier
            .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(oauth2Properties.google.clientId))
            .build()
}
