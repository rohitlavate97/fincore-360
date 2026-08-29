package com.fincore

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FinCoreApplication

fun main(args: Array<String>) {
    SpringApplication.run(FinCoreApplication::class.java, *args)
}
