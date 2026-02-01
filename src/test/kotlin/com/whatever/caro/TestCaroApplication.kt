package com.whatever.caro

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<CaroApplication>().with(TestcontainersConfiguration::class).run(*args)
}
