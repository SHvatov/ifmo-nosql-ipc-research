package com.shvatov.diploma

import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
@ImportAutoConfiguration(RedisIPCAutoConfiguration::class)
class DiplomaMqApplication

fun main(args: Array<String>) {
    runApplication<DiplomaMqApplication>(*args)
}
