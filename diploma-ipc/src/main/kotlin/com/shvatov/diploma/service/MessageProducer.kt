package com.shvatov.diploma.service

import com.shvatov.diploma.dto.Message
import com.shvatov.redis.ipc.annotation.publisher.Publish
import com.shvatov.redis.ipc.annotation.publisher.Publisher
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

@Publisher
interface MessageProducer {

    @Publish(
        publishRequestToChannel = "diploma.nosql.outgoing-topic",
        awaitAtLeastOneReceiver = true,
        retries = 5,
        retriesBackoffDuration = 5,
        retriesBackoffDurationUnit = TimeUnit.SECONDS,
    )
    fun sendMessage(message: Message): Mono<Unit>

}
