package com.shvatov.diploma.nosql.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.nosql.producer.RedisMessageProducer
import com.shvatov.diploma.service.MessageRegistry
import com.shvatov.diploma.util.logEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

@Component
class RedisMessageConsumer(
    @Lazy private val objectMapper: ObjectMapper,
    @Lazy private val messageRegistry: MessageRegistry,
    @Lazy private val messageProducer: RedisMessageProducer
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val msg = objectMapper.readValue<com.shvatov.diploma.dto.Message>(message.body)
            .let { messageRegistry.register(it) }
        log.logEvent("Received message in topic {}", String(message.channel))
        if (msg.compareAndSetStatus(MessageStatus.CREATED, MessageStatus.RECEIVED)) {
            messageProducer.sendMessage(msg.withStatus(MessageStatus.PROCESSED))
        } else {
            msg.withStatus(MessageStatus.RECEIVED)
                .withStatus(MessageStatus.FINALIZED)
        }
        log.logEvent("Finished consuming message in topic {}", String(message.channel))
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(RedisMessageConsumer::class.java)
    }

}
