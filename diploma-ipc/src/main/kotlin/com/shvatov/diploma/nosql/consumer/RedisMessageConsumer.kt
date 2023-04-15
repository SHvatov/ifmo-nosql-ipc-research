package com.shvatov.diploma.nosql.consumer

import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.service.MessageProducer
import com.shvatov.diploma.service.MessageRegistry
import com.shvatov.diploma.util.logEvent
import com.shvatov.redis.ipc.annotation.listener.ChannelName
import com.shvatov.redis.ipc.annotation.listener.Listener
import com.shvatov.redis.ipc.annotation.listener.Payload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy

class RedisMessageConsumer(
    @Lazy private val messageRegistry: MessageRegistry,
    @Lazy private val messageProducer: MessageProducer
) {

    @Listener(channels = ["diploma.nosql.incoming-topic"])
    fun onMessage(
        @ChannelName channel: String,
        @Payload msg: Message
    ) {
        messageRegistry.register(msg)
        log.logEvent("Received message in topic {}", channel)
        if (msg.compareAndSetStatus(MessageStatus.CREATED, MessageStatus.RECEIVED)) {
            messageProducer.sendMessage(msg.withStatus(MessageStatus.PROCESSED))
        } else {
            msg.withStatus(MessageStatus.RECEIVED)
                .withStatus(MessageStatus.FINALIZED)
        }
        log.logEvent("Finished consuming message in topic {}", channel)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(RedisMessageConsumer::class.java)
    }

}
