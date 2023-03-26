package com.shvatov.diploma.mq.config

import com.shvatov.diploma.dto.Message
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.requests.CreateTopicsRequest.NO_REPLICATION_FACTOR
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer


@Configuration
class KafkaConfig {

    @Value(value = "\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapAddress: String

    @Value(value = "\${diploma.mq.incoming-topic}")
    private lateinit var incomingTopic: String

    @Value(value = "\${diploma.mq.outgoing-topic}")
    private lateinit var outgoingTopic: String

    @Value(value = "\${diploma.mq.group-id}")
    private lateinit var groupId: String

    @Bean
    fun kafkaAdmin() =
        KafkaAdmin(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress
            )
        )

    @Bean
    fun incomingTopic() =
        NewTopic(incomingTopic, 1, NO_REPLICATION_FACTOR)

    @Bean
    fun outgoingTopic() =
        NewTopic(outgoingTopic, 1, NO_REPLICATION_FACTOR)

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Message> {
        val factory = DefaultKafkaProducerFactory<String, Message>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "1",
                ProducerConfig.BATCH_SIZE_CONFIG to "1",
            )
        )
        return KafkaTemplate(factory)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Message> =
        ConcurrentKafkaListenerContainerFactory<String, Message>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
                    ConsumerConfig.GROUP_ID_CONFIG to "com.shvatov.consumer.$groupId",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ),
                StringDeserializer(),
                JsonDeserializer<Message>().apply {
                    addTrustedPackages("com.shvatov.*")
                }
            ).apply {
                containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            }
            setConcurrency(1)
        }
}
