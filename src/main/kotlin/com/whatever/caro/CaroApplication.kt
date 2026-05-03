package com.whatever.caro

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.ZoneId
import java.util.TimeZone

@SpringBootApplication
class CaroApplication {
    @PostConstruct
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }
}

fun main(
    args: Array<String>,
) {
    loadDotEnv()
    runApplication<CaroApplication>(*args)
}

private fun loadDotEnv() {
    val envFile = java.io.File(".env").takeIf { it.exists() }
        ?: java.io.File(System.getProperty("user.dir"), ".env").takeIf { it.exists() }
        ?: return

    envFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            val trimmedKey = key.trim()
            val trimmedValue = value.trim().removeSurrounding("'").removeSurrounding("\"")
            // 이미 환경변수로 주입된 값은 덮어쓰지 않음
            if (System.getenv(trimmedKey) == null) {
                System.setProperty(trimmedKey, trimmedValue)
            }
        }
}
