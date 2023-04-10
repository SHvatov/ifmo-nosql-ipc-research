package com.shvatov.diploma

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

// test comment
@EnableAsync
@SpringBootApplication
class DiplomaMqApplication

fun main(args: Array<String>) {
    runApplication<DiplomaMqApplication>(*args)
}
