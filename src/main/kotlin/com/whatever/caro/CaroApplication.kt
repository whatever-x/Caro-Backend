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

fun main(args: Array<String>) {
    runApplication<CaroApplication>(*args)
}
