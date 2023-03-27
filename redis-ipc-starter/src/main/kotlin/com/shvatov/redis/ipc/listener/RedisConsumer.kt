package com.shvatov.redis.ipc.listener

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RedisConsumer(
    val channels: Array<String> = [],
    val pattern: String = ""
)
