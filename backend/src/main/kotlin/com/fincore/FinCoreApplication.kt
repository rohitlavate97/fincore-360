package com.fincore

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FinCoreApplication

fun main(args: Array<String>) {
    SpringApplication.run(FinCoreApplication::class.java, *args)
}
