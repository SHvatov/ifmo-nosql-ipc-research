package com.shvatov.diploma.mq.consumer

import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.dto.MessageStatus
import com.shvatov.diploma.mq.producer.KafkaMessageProducer
import com.shvatov.diploma.service.MessageRegistry
import com.shvatov.diploma.util.logEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class KafkaMessageConsumer(
    private val messageRegistry: MessageRegistry,
    private val messageProducer: KafkaMessageProducer
) {

    @KafkaListener(topics = ["\${diploma.mq.incoming-topic}"])
    fun processMessage(record: ConsumerRecord<String, Message>, ack: Acknowledgment) {
        val message = messageRegistry.register(record.value())
        log.logEvent("Received message in topic {}", record.topic())
        if (message.compareAndSetStatus(MessageStatus.CREATED, MessageStatus.RECEIVED)) {
            messageProducer.sendMessage(message.withStatus(MessageStatus.PROCESSED))
        } else {
            message.withStatus(MessageStatus.RECEIVED)
                .withStatus(MessageStatus.FINALIZED)
        }
        log.logEvent("Finished consuming message in topic {}", record.topic())
        ack.acknowledge()
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(KafkaMessageConsumer::class.java)
    }

}
