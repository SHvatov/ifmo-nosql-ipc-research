package com.shvatov.diploma.nosql.producer

import com.fasterxml.jackson.databind.ObjectMapper
import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.service.MessageProducer
import com.shvatov.diploma.util.logEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Component

@Component
class RedisMessageProducer(
    private val objectMapper: ObjectMapper,
    private val outgoingTopic: ChannelTopic,
    private val redisTemplate: RedisTemplate<String, String>
) : MessageProducer {

    override fun sendMessage(message: Message) {
        log.logEvent("Sending message to Redis")
        runCatching {
            val messageAsString = objectMapper.writeValueAsString(message)
            redisTemplate.convertAndSend(outgoingTopic.topic, messageAsString)
        }.onFailure {
            log.error("Error while sending message with id {} to Redis", message.id, it)
            return
        }
        log.logEvent("Successfully sent message to Redis")
        message.withStatus(MessageStatus.SENT)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(RedisMessageProducer::class.java)
    }

}
