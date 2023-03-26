package com.shvatov.diploma.mq.producer

import com.shvatov.diploma.annotation.MessageQueueEnabled
import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.service.MessageProducer
import com.shvatov.diploma.util.logEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@MessageQueueEnabled
class KafkaMessageProducer(private val kafkaTemplate: KafkaTemplate<String, Message>) : MessageProducer {

    @Value(value = "\${diploma.mq.outgoing-topic}")
    private lateinit var outgoingTopic: String

    override fun sendMessage(message: Message) {
        log.logEvent("Sending message to Kafka")
        runCatching {
            kafkaTemplate.send(outgoingTopic, message).get(10, TimeUnit.SECONDS)
        }.onFailure {
            log.error("Error while sending message with id {} to Kafka", message.id, it)
            return
        }
        log.logEvent("Successfully sent message to Kafka")
        message.withStatus(MessageStatus.SENT)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(KafkaMessageProducer::class.java)
    }
}