package com.whatever.caro

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CaroApplication

fun main(args: Array<String>) {
	runApplication<CaroApplication>(*args)
}
