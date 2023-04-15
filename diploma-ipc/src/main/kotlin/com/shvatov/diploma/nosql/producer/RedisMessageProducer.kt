package com.shvatov.diploma.nosql.producer

import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.service.MessageProducer
import org.springframework.stereotype.Component

@Component
class RedisMessageProducer(private val messageProducer: MessageProducer) {

    fun sendMessage(message: Message) {
        messageProducer.sendMessage(message).block()
        message.withStatus(MessageStatus.SENT)
    }

}
